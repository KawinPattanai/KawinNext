#!/usr/bin/env node
'use strict';
// Analyze graph topology for guided-tour design (understand-anything tour-builder, Phase 1)

const fs = require('fs');

function fail(msg) {
  process.stderr.write('ERROR: ' + msg + '\n');
  process.exit(1);
}

const [inPath, outPath] = process.argv.slice(2);
if (!inPath || !outPath) fail('usage: node ua-tour-analyze.js <input.json> <output.json>');

let input;
try {
  input = JSON.parse(fs.readFileSync(inPath, 'utf8'));
} catch (e) {
  fail('cannot read/parse input: ' + e.message);
}

const nodes = Array.isArray(input.nodes) ? input.nodes : [];
const edges = Array.isArray(input.edges) ? input.edges : [];
const layers = Array.isArray(input.layers) ? input.layers : [];

const nodeById = new Map();
for (const n of nodes) {
  if (n && typeof n.id === 'string') nodeById.set(n.id, n);
}

// ---- A/B: fan-in / fan-out ----
const fanIn = new Map();
const fanOut = new Map();
for (const id of nodeById.keys()) { fanIn.set(id, 0); fanOut.set(id, 0); }
const undirectedAdj = new Map(); // for paths
const fwdAdj = new Map(); // forward imports/calls
for (const id of nodeById.keys()) { undirectedAdj.set(id, new Set()); fwdAdj.set(id, new Set()); }

for (const e of edges) {
  if (!e || typeof e.source !== 'string' || typeof e.target !== 'string') continue;
  if (!nodeById.has(e.source) || !nodeById.has(e.target)) continue;
  if (e.source === e.target) continue;
  fanOut.set(e.source, fanOut.get(e.source) + 1);
  fanIn.set(e.target, fanIn.get(e.target) + 1);
  undirectedAdj.get(e.source).add(e.target);
  undirectedAdj.get(e.target).add(e.source);
  if (e.type === 'imports' || e.type === 'calls') {
    fwdAdj.get(e.source).add(e.target);
  }
}

const fanInRanking = [...fanIn.entries()]
  .sort((a, b) => b[1] - a[1])
  .slice(0, 20)
  .map(([id, v]) => ({ id, fanIn: v, name: nodeById.get(id).name }));

const fanOutRanking = [...fanOut.entries()]
  .sort((a, b) => b[1] - a[1])
  .slice(0, 20)
  .map(([id, v]) => ({ id, fanOut: v, name: nodeById.get(id).name }));

// ---- C: entry point candidates ----
const entryNames = new Set(['index.ts','index.js','main.ts','main.js','app.ts','app.js','server.ts','server.js',
  'mod.rs','main.go','main.py','main.rs','manage.py','app.py','wsgi.py','asgi.py','run.py','__main__.py',
  'Application.java','Main.java','Program.cs','config.ru','index.php','App.swift','Application.kt','main.cpp','main.c']);

const fanOutVals = [...fanOut.values()].sort((a, b) => b - a);
const fanInVals = [...fanIn.values()].sort((a, b) => a - b);
const top10pctFanOut = fanOutVals.length ? fanOutVals[Math.max(0, Math.ceil(fanOutVals.length * 0.10) - 1)] : 0;
const bottom25pctFanIn = fanInVals.length ? fanInVals[Math.min(fanInVals.length - 1, Math.floor(fanInVals.length * 0.25))] : 0;

const candidates = [];
for (const n of nodes) {
  if (!nodeById.has(n.id)) continue;
  let score = 0;
  const filePath = typeof n.filePath === 'string' ? n.filePath.replace(/\\/g, '/') : '';
  const depth = filePath.split('/').length - 1;
  if (n.type === 'document') {
    if (n.name === 'README.md' && depth === 0) score += 5;
    else if (/\.md$/i.test(n.name) && depth === 0) score += 2;
  } else if (n.type === 'file') {
    if (entryNames.has(n.name)) score += 3;
    if (depth <= 1) score += 1;
    if (fanOut.get(n.id) >= top10pctFanOut && top10pctFanOut > 0) score += 1;
    if (fanIn.get(n.id) <= bottom25pctFanIn) score += 1;
  }
  if (score > 0) {
    candidates.push({ id: n.id, score, name: n.name, type: n.type, summary: n.summary || '' });
  }
}
candidates.sort((a, b) => b.score - a.score);
const entryPointCandidates = candidates.slice(0, 5);

// ---- D: BFS from top code entry point (imports/calls edges only) ----
const codeEntry = entryPointCandidates.find(c => c.type === 'file') || entryPointCandidates[0];
const bfsTraversal = { startNode: null, order: [], depthMap: {}, byDepth: {} };
if (codeEntry) {
  bfsTraversal.startNode = codeEntry.id;
  const visited = new Set([codeEntry.id]);
  let frontier = [codeEntry.id];
  let depth = 0;
  while (frontier.length && depth <= 10) {
    const levelIds = [];
    const next = [];
    for (const id of frontier) {
      levelIds.push(id);
      bfsTraversal.depthMap[id] = depth;
      bfsTraversal.order.push(id);
      for (const nb of fwdAdj.get(id)) {
        if (!visited.has(nb)) { visited.add(nb); next.push(nb); }
      }
    }
    if (levelIds.length) bfsTraversal.byDepth[String(depth)] = levelIds;
    frontier = next;
    depth++;
  }
}

// ---- E: non-code inventory ----
const docTypes = new Set(['document']);
const infraTypes = new Set(['service', 'pipeline', 'resource']);
const dataTypes = new Set(['table', 'schema', 'endpoint']);
const configTypes = new Set(['config']);
const nonCodeFiles = { documentation: [], infrastructure: [], data: [], config: [] };
for (const n of nodes) {
  const rec = { id: n.id, name: n.name, type: n.type, summary: n.summary || '' };
  if (docTypes.has(n.type)) nonCodeFiles.documentation.push(rec);
  else if (infraTypes.has(n.type)) nonCodeFiles.infrastructure.push(rec);
  else if (dataTypes.has(n.type)) nonCodeFiles.data.push(rec);
  else if (configTypes.has(n.type)) nonCodeFiles.config.push(rec);
}

// ---- F: tightly coupled clusters ----
// pairs with edges in both directions
const pairSet = new Set(); // "a|b"
const directedPair = new Set();
for (const e of edges) {
  if (!e || !nodeById.has(e.source) || !nodeById.has(e.target) || e.source === e.target) continue;
  directedPair.add(e.source + '|' + e.target);
}
const bidirAdj = new Map();
for (const key of directedPair) {
  const [a, b] = key.split('|');
  if (directedPair.has(b + '|' + a)) {
    if (!bidirAdj.has(a)) bidirAdj.set(a, new Set());
    bidirAdj.get(a).add(b);
  }
}
// expand clusters: start from bidirectional pair, add nodes connecting to >=2 members
const usedInCluster = new Set();
const clusters = [];
for (const key of bidirAdj.keys()) {
  const seedA = key;
  if (usedInCluster.has(seedA)) continue;
  const members = new Set([seedA]);
  // add bidir partners, then expand
  const queue = [seedA, ...bidirAdj.get(seedA)];
  for (const q of queue) members.add(q);
  let changed = true;
  while (changed && members.size < 5) {
    changed = false;
    for (const candId of nodeById.keys()) {
      if (members.has(candId)) continue;
      let links = 0;
      for (const m of members) {
        if ((undirectedAdj.get(candId) || new Set()).has(m)) links++;
      }
      if (links >= 2) { members.add(candId); changed = true; if (members.size >= 5) break; }
    }
  }
  // count internal edges
  let edgeCount = 0;
  const arr = [...members];
  for (let i = 0; i < arr.length; i++) {
    for (let j = i + 1; j < arr.length; j++) {
      if (directedPair.has(arr[i] + '|' + arr[j])) edgeCount++;
      if (directedPair.has(arr[j] + '|' + arr[i])) edgeCount++;
    }
  }
  if (members.size >= 2 && edgeCount >= 2) {
    clusters.push({ nodes: arr, edgeCount });
    for (const m of arr) usedInCluster.add(m);
  }
}
clusters.sort((a, b) => b.edgeCount - a.edgeCount);
const topClusters = clusters.slice(0, 10);

// ---- G: layers ----
const layerOut = {
  count: layers.length,
  list: layers.map(l => ({ id: l.id, name: l.name, description: l.description || '' })),
};

// ---- H: node summary index ----
const nodeSummaryIndex = {};
for (const n of nodes) {
  nodeSummaryIndex[n.id] = { name: n.name, type: n.type, summary: n.summary || '' };
}

const result = {
  scriptCompleted: true,
  entryPointCandidates,
  fanInRanking,
  fanOutRanking,
  bfsTraversal,
  nonCodeFiles,
  clusters: topClusters,
  layers: layerOut,
  nodeSummaryIndex,
  totalNodes: nodes.length,
  totalEdges: edges.length,
};

try {
  fs.writeFileSync(outPath, JSON.stringify(result, null, 2));
  process.exit(0);
} catch (e) {
  fail('cannot write output: ' + e.message);
}
