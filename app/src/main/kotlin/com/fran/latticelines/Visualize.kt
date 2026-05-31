package com.fran.latticelines

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.zip.GZIPOutputStream

fun main() {
    println("Computing lattice data for grids 2×2 → 30×30…")
    val json = buildAllGridsJson()
    println("Compressing…")
    val compressed = gzipBase64(json)
    println("Compressed ${json.length / 1_000_000.0}MB → ${compressed.length / 1_000_000.0}MB (base64)")
    File("lattice.html").writeText(buildHtmlPage(compressed))
    println("Generated: lattice.html — open in a browser.")
}

private fun gzipBase64(input: String): String {
    val baos = ByteArrayOutputStream()
    GZIPOutputStream(baos).use { it.write(input.toByteArray(Charsets.UTF_8)) }
    return Base64.getEncoder().encodeToString(baos.toByteArray())
}

private fun buildAllGridsJson(): String = buildString {
    append('{')
    (2..30).forEachIndexed { gridIdx, n ->
        if (gridIdx > 0) append(',')
        val points = gridPoints(n, n)
        val segments = allSegments(points)
        val bySquaredLength = segments.groupBy { it.squaredLength }

        append('"').append(n).append("\":{\"cols\":").append(n)
            .append(",\"rows\":").append(n).append(",\"lengths\":{")

        bySquaredLength.entries.sortedBy { it.key }.forEachIndexed { i, (sq, segs) ->
            if (i > 0) append(',')
            val shapes = shapesForSquaredLength(sq)
            val shapeIndex = shapes.withIndex().associate { (idx, s) -> s to idx }
            val grouped = Array(shapes.size) { mutableListOf<Segment>() }
            segs.forEach { seg ->
                val key = (seg.b.x - seg.a.x) to (seg.b.y - seg.a.y)
                grouped[shapeIndex.getValue(key)].add(seg)
            }

            append('"').append(sq).append("\":{\"shapes\":[")
            shapes.forEachIndexed { si, (dx, dy) ->
                if (si > 0) append(',')
                append('[').append(dx).append(',').append(dy).append(']')
            }
            // Encode each shape's placements as a flat base-36 string of anchor coords
            // (one char per coord, 2 chars per segment). The end point is reconstructed
            // in JS from the known step vector, halving coordinate data before compression.
            val b36 = "0123456789abcdefghijklmnopqrstuvwxyz"
            append("],\"byShape\":[")
            grouped.forEachIndexed { si, segList ->
                if (si > 0) append(',')
                append('"')
                segList.forEach { s -> append(b36[s.a.x]).append(b36[s.a.y]) }
                append('"')
            }
            append("]}")
        }
        append("}}")
    }
    append('}')
}

private fun buildHtmlPage(compressedB64: String): String = """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Lattice Lines</title>
  <style>
    *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
    :root {
      color-scheme: dark;
      --blue: #3b82f6; --blue-d: #60a5fa; --blue-l: #1e3a5f;
      --bg: #0f172a; --surface: #1e293b; --border: #334155;
      --text: #f1f5f9; --muted: #94a3b8;
    }
    html, body { height: 100%; font-family: system-ui, -apple-system, sans-serif; font-size: 14px; color: var(--text); background: var(--bg); }
    body { display: flex; flex-direction: column; overflow: hidden; }

    #loading {
      position: fixed; inset: 0; background: var(--bg); z-index: 100;
      display: flex; flex-direction: column; align-items: center; justify-content: center;
      gap: 12px;
    }
    .spinner {
      width: 28px; height: 28px; border: 3px solid var(--border);
      border-top-color: var(--blue); border-radius: 50%;
      animation: spin .7s linear infinite;
    }
    @keyframes spin { to { transform: rotate(360deg); } }
    #loading-msg { font-size: 13px; color: var(--muted); }

    header {
      display: flex; align-items: center; gap: 10px;
      padding: 0 20px; height: 52px;
      background: var(--surface); border-bottom: 1px solid var(--border); flex-shrink: 0;
    }
    h1 { font-size: 16px; font-weight: 600; letter-spacing: -.02em; }
    .vr { width: 1px; height: 20px; background: var(--border); flex-shrink: 0; }
    .label { font-size: 13px; color: var(--muted); }
    select {
      padding: 5px 10px; border: 1px solid var(--border); border-radius: 6px;
      font: inherit; background: var(--surface); cursor: pointer;
    }
    select#length-select { min-width: 230px; }
    select:focus { outline: 2px solid var(--blue); outline-offset: 1px; border-color: transparent; }
    .chip { font-size: 12px; color: var(--muted); margin-left: auto; white-space: nowrap; }

    .mode-toggle { display: flex; border: 1px solid var(--border); border-radius: 6px; overflow: hidden; background: var(--border); gap: 1px; }
    .toggle-btn {
      border: none; padding: 5px 13px; background: var(--surface); color: var(--muted);
      font: inherit; font-size: 13px; cursor: pointer;
    }
    .toggle-btn:hover:not(.active) { background: #243044; }
    .toggle-btn.active { background: #334155; color: var(--text); }

    button#random-btn {
      padding: 5px 12px; border: 1px solid var(--border); border-radius: 6px;
      background: var(--surface); font: inherit; font-size: 13px; cursor: pointer;
    }
    button#random-btn:hover:not(:disabled) { background: var(--bg); }
    button#random-btn:disabled { opacity: .35; cursor: default; }

    main { display: flex; flex: 1; overflow: hidden; }

    #tiles-panel { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
    #tiles-hdr { padding: 10px 16px 6px; font-size: 12px; font-weight: 500; color: var(--muted); flex-shrink: 0; }
    #tiles {
      flex: 1; overflow-y: auto; padding: 8px 16px 16px;
      display: flex; flex-wrap: wrap; align-content: flex-start; gap: 6px;
    }

    #stats-bar {
      flex-shrink: 0; display: flex; gap: 0;
      border-top: 1px solid var(--border); background: var(--surface);
    }
    .stats-half { flex: 1; padding: 10px 16px 12px; }
    .stats-half + .stats-half { border-left: 1px solid var(--border); }
    .stats-title { font-size: 11px; font-weight: 600; color: var(--muted); text-transform: uppercase; letter-spacing: .05em; margin-bottom: 6px; }
    .stats-tbl { width: 100%; border-collapse: collapse; font-size: 12px; }
    .stats-tbl th { color: var(--muted); font-weight: 500; padding: 2px 10px 4px 0; text-align: left; }
    .stats-tbl td { color: var(--text); padding: 2px 10px 2px 0; }
    .stats-tbl td:last-child { text-align: right; color: var(--muted); padding-right: 0; }
    .stats-tbl tr.hl td { color: var(--blue-d); font-weight: 600; }
    .stats-tbl tr.hl td:last-child { color: var(--blue-d); }
    svg.tile {
      width: 72px; height: 72px; display: block; flex-shrink: 0; cursor: pointer;
      border: 2px solid var(--border); border-radius: 6px; background: var(--surface);
      transition: border-color .1s, box-shadow .1s;
    }
    svg.tile:hover { border-color: var(--blue); box-shadow: 0 0 10px rgba(59,130,246,.35); }
    svg.tile.active { border-color: #60a5fa; }
    svg.tile.unused { opacity: 0.25; filter: grayscale(1); }
    svg.tile.unused:hover { border-color: #475569; box-shadow: none; }
    svg.tile.unused.active { border-color: #475569; }

    #detail {
      width: 300px; flex-shrink: 0; border-left: 1px solid var(--border);
      background: var(--surface); overflow-y: auto; padding: 20px;
    }
    #detail h2 { font-size: 14px; font-weight: 600; }
    .d-idx { font-size: 12px; color: var(--muted); margin-top: 2px; }
    svg.d-svg { width: 100%; aspect-ratio: 1; border: 1px solid var(--border); border-radius: 8px; margin: 12px 0; display: block; }
    .d-meta { font-size: 13px; color: var(--muted); line-height: 2; }
    .d-meta b { color: var(--text); }
    .d-meta small { font-size: 11px; color: #475569; }
    .d-empty { font-size: 13px; color: var(--muted); padding-top: 48px; text-align: center; line-height: 2; }
    .d-nav { display: flex; gap: 6px; margin-top: 16px; }
    .d-nav button {
      flex: 1; padding: 5px; border: 1px solid var(--border); border-radius: 6px;
      background: var(--surface); font: inherit; font-size: 13px; cursor: pointer;
    }
    .d-nav button:hover:not(:disabled) { background: var(--bg); }
    .d-nav button:disabled { opacity: .35; cursor: default; }
  </style>
</head>
<body>
<div id="loading">
  <div class="spinner"></div>
  <div id="loading-msg">Decompressing data…</div>
</div>
<header>
  <h1>Lattice Lines</h1>
  <span class="vr"></span>
  <span class="label">Grid</span>
  <select id="grid-select"></select>
  <span class="vr"></span>
  <span class="label">Length</span>
  <select id="length-select"></select>
  <span class="vr"></span>
  <div class="mode-toggle">
    <button class="toggle-btn active" id="mode-all">All segments</button>
    <button class="toggle-btn" id="mode-shapes">Shapes</button>
  </div>
  <button id="random-btn">Random</button>
  <span class="chip" id="chip"></span>
</header>
<main>
  <section id="tiles-panel">
    <div id="tiles-hdr"></div>
    <div id="tiles"></div>
    <div id="stats-bar">
      <div class="stats-half">
        <div class="stats-title">Top 5 lengths</div>
        <table class="stats-tbl">
          <thead><tr><th>#</th><th>Length</th><th>Segments</th></tr></thead>
          <tbody id="top5-tbody"></tbody>
        </table>
      </div>
      <div class="stats-half">
        <div class="stats-title">Bottom 5 lengths</div>
        <table class="stats-tbl">
          <thead><tr><th>#</th><th>Length</th><th>Segments</th></tr></thead>
          <tbody id="bot5-tbody"></tbody>
        </table>
      </div>
    </div>
  </section>
  <aside id="detail"><div id="detail-body"></div></aside>
</main>
<script>
var COMPRESSED_B64 = '$compressedB64';

async function decompress(b64) {
  var bin = atob(b64);
  var bytes = new Uint8Array(bin.length);
  for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  var ds = new DecompressionStream('gzip');
  var writer = ds.writable.getWriter();
  writer.write(bytes);
  writer.close();
  var reader = ds.readable.getReader();
  var chunks = [];
  while (true) {
    var result = await reader.read();
    if (result.done) break;
    chunks.push(result.value);
  }
  var totalLen = chunks.reduce(function (n, c) { return n + c.length; }, 0);
  var buf = new Uint8Array(totalLen);
  var pos = 0;
  chunks.forEach(function (c) { buf.set(c, pos); pos += c.length; });
  return JSON.parse(new TextDecoder().decode(buf));
}

decompress(COMPRESSED_B64).then(function (ALL_DATA) {
  // Decode flat base-36 anchor strings → [x1,y1,x2,y2] segment arrays.
  // Each pair of chars encodes one anchor point; end point = anchor + shape vector.
  var b36 = {};
  for (var i = 0; i < 30; i++) b36[i < 10 ? String(i) : String.fromCharCode(87 + i)] = i;
  Object.keys(ALL_DATA).forEach(function (n) {
    var gEntry = ALL_DATA[n];
    Object.keys(gEntry.lengths).forEach(function (sq) {
      var entry = gEntry.lengths[sq];
      entry.byShape = entry.byShape.map(function (s, si) {
        var dx = entry.shapes[si][0], dy = entry.shapes[si][1];
        var count = s.length >> 1;
        var result = new Array(count);
        for (var j = 0; j < count; j++) {
          var ax = b36[s[j * 2]], ay = b36[s[j * 2 + 1]];
          result[j] = [ax, ay, ax + dx, ay + dy];
        }
        return result;
      });
    });
  });
  document.getElementById('loading').remove();
  setupUI(ALL_DATA);
});

function setupUI(ALL_DATA) {
(function () {
  var SVG_NS = 'http://www.w3.org/2000/svg';

  var PALETTE = [
    '#f87171','#fb923c','#facc15','#4ade80','#34d399','#22d3ee',
    '#60a5fa','#818cf8','#a78bfa','#e879f9','#f472b6','#2dd4bf',
    '#a3e635','#fbbf24','#c084fc','#f43f5e'
  ];

  var COLS, ROWS, STEP, PT_R, SEG_W, GRID_W, DENSE;
  var DATA, sortedSqs;

  function updateGridVars(n) {
    var entry = ALL_DATA[n];
    COLS = entry.cols; ROWS = entry.rows;
    DATA = entry.lengths;
    sortedSqs = Object.keys(DATA).map(Number).sort(function (a, b) { return a - b; });
    STEP   = 90 / (Math.max(COLS, ROWS) - 1);
    PT_R   = Math.max(1.2, Math.min(3.5, STEP * 0.35));
    SEG_W  = Math.max(0.8, Math.min(2.5, STEP * 0.25));
    GRID_W = Math.max(0.3, Math.min(0.7, STEP * 0.07));
    DENSE  = STEP < 5;
  }

  function readableLen(sq) {
    var d = Math.sqrt(sq);
    return Math.round(d) === d ? String(Math.round(d)) : '√' + sq;
  }

  function getColor(si) { return PALETTE[si % PALETTE.length]; }

  function shapeName(dx, dy) {
    if (dy === 0) return 'horizontal';
    if (dx === 0) return 'vertical';
    return '(' + dx + ', ' + dy + ')';
  }

  function toSvgPt(px, py) { return [5 + px * STEP, 5 + py * STEP]; }

  function mkSvgEl(tag, attrs) {
    var el = document.createElementNS(SVG_NS, tag);
    Object.keys(attrs).forEach(function (k) { el.setAttribute(k, String(attrs[k])); });
    return el;
  }

  function makeGridLines() {
    var g = mkSvgEl('g', { stroke: '#2d3f55', 'stroke-width': GRID_W });
    var x2 = 5 + (COLS - 1) * STEP, y2 = 5 + (ROWS - 1) * STEP;
    for (var c = 0; c < COLS; c++) {
      var cx = 5 + c * STEP;
      g.appendChild(mkSvgEl('line', { x1: cx, y1: 5, x2: cx, y2: y2 }));
    }
    for (var r = 0; r < ROWS; r++) {
      var ry = 5 + r * STEP;
      g.appendChild(mkSvgEl('line', { x1: 5, y1: ry, x2: x2, y2: ry }));
    }
    return g;
  }

  function makeBoundingBox() {
    return mkSvgEl('rect', {
      x: 5, y: 5, width: (COLS - 1) * STEP, height: (ROWS - 1) * STEP,
      fill: 'none', stroke: '#2d3f55', 'stroke-width': '1'
    });
  }

  function makeTileSvg(seg, color) {
    var p1 = toSvgPt(seg[0], seg[1]), p2 = toSvgPt(seg[2], seg[3]);
    var svg = mkSvgEl('svg', { viewBox: '0 0 100 100' });
    svg.setAttribute('class', 'tile');
    svg.appendChild(DENSE ? makeBoundingBox() : makeGridLines());
    svg.appendChild(mkSvgEl('line', { x1: p1[0], y1: p1[1], x2: p2[0], y2: p2[1],
      stroke: color, 'stroke-width': SEG_W, 'stroke-linecap': 'round' }));
    svg.appendChild(mkSvgEl('circle', { cx: p1[0], cy: p1[1], r: PT_R, fill: color, opacity: 0.45 }));
    svg.appendChild(mkSvgEl('circle', { cx: p2[0], cy: p2[1], r: PT_R, fill: color }));
    return svg;
  }

  function shapeAnchor(dx, dy) {
    if (dy === 0) return [15, 50];
    if (dx === 0) return [50, 85];
    return [dx > 0 ? 15 : 85, 85];
  }

  function makeShapeTile(dx, dy, color) {
    var svg = mkSvgEl('svg', { viewBox: '0 0 100 100' });
    svg.setAttribute('class', 'tile');
    svg.appendChild(mkSvgEl('rect', { x: 5, y: 5, width: 90, height: 90,
      fill: '#0a1525', rx: 3 }));
    var maxDim = Math.max(Math.abs(dx), Math.abs(dy)) || 1;
    var scale = 70 / maxDim;
    var anch = shapeAnchor(dx, dy);
    var ax = anch[0], ay = anch[1];
    var ex = ax + dx * scale, ey = ay - dy * scale;
    svg.appendChild(mkSvgEl('line', { x1: ax, y1: ay, x2: ex, y2: ey,
      stroke: color, 'stroke-width': '2.5', 'stroke-linecap': 'round' }));
    svg.appendChild(mkSvgEl('circle', { cx: ax, cy: ay, r: 3.5,
      fill: 'none', stroke: color, 'stroke-width': 1.5 }));
    svg.appendChild(mkSvgEl('circle', { cx: ex, cy: ey, r: 3.5, fill: color }));
    return svg;
  }

  function makeDetailSvg(seg, color) {
    var p1 = toSvgPt(seg[0], seg[1]), p2 = toSvgPt(seg[2], seg[3]);
    var svg = mkSvgEl('svg', { viewBox: '0 0 100 100' });
    svg.setAttribute('class', 'd-svg');
    svg.appendChild(makeGridLines());
    var dotR = Math.max(0.8, Math.min(1.5, STEP * 0.15));
    var dots = mkSvgEl('g', { fill: '#3d5068' });
    for (var x = 0; x < COLS; x++) {
      for (var y = 0; y < ROWS; y++) {
        var p = toSvgPt(x, y);
        dots.appendChild(mkSvgEl('circle', { cx: p[0], cy: p[1], r: dotR }));
      }
    }
    svg.appendChild(dots);
    svg.appendChild(mkSvgEl('line', { x1: p1[0], y1: p1[1], x2: p2[0], y2: p2[1],
      stroke: color, 'stroke-width': Math.max(1.5, SEG_W * 1.2), 'stroke-linecap': 'round' }));
    svg.appendChild(mkSvgEl('circle', { cx: p1[0], cy: p1[1], r: Math.max(2, PT_R * 1.15),
      fill: color, opacity: 0.5 }));
    svg.appendChild(mkSvgEl('circle', { cx: p2[0], cy: p2[1], r: Math.max(2, PT_R * 1.15),
      fill: color }));
    return svg;
  }

  function makeDetailShapeSvg(dx, dy, color) {
    var svg = mkSvgEl('svg', { viewBox: '0 0 100 100' });
    svg.setAttribute('class', 'd-svg');
    svg.appendChild(mkSvgEl('rect', { x: 0, y: 0, width: 100, height: 100, fill: '#0a1525' }));
    var maxDim = Math.max(Math.abs(dx), Math.abs(dy)) || 1;
    var scale = 70 / maxDim;
    var anch = shapeAnchor(dx, dy);
    var ax = anch[0], ay = anch[1];
    var ex = ax + dx * scale, ey = ay - dy * scale;
    svg.appendChild(mkSvgEl('line', { x1: ax, y1: ay, x2: ex, y2: ey,
      stroke: color, 'stroke-width': '3', 'stroke-linecap': 'round' }));
    svg.appendChild(mkSvgEl('circle', { cx: ax, cy: ay, r: 5,
      fill: 'none', stroke: color, 'stroke-width': 2 }));
    svg.appendChild(mkSvgEl('circle', { cx: ex, cy: ey, r: 5, fill: color }));
    return svg;
  }

  var currentSq    = null;
  var currentMode  = 'all';
  var flatSegs     = [];
  var activeIdx    = null;
  var activeTileEl = null;

  var top5TbodyEl  = document.getElementById('top5-tbody');
  var bot5TbodyEl  = document.getElementById('bot5-tbody');
  var gridSelect   = document.getElementById('grid-select');
  var lengthSelect = document.getElementById('length-select');
  var tilesEl      = document.getElementById('tiles');
  var tilesHdrEl   = document.getElementById('tiles-hdr');
  var detailBodyEl = document.getElementById('detail-body');
  var chipEl       = document.getElementById('chip');
  var modeAllBtn   = document.getElementById('mode-all');
  var modeShapesBtn= document.getElementById('mode-shapes');
  var randomBtn    = document.getElementById('random-btn');

  function renderStats() {
    var ranked = sortedSqs.map(function (sq) {
      return { sq: sq, count: DATA[sq].byShape.reduce(function (s, a) { return s + a.length; }, 0) };
    }).sort(function (a, b) { return b.count - a.count; });

    function fillTable(tbody, rows) {
      tbody.innerHTML = '';
      rows.forEach(function (item) {
        var tr = document.createElement('tr');
        if (item.sq === currentSq) tr.className = 'hl';
        tr.innerHTML = '<td>' + item.rank + '</td><td>' + readableLen(item.sq) + '</td><td>' + item.count.toLocaleString() + '</td>';
        tbody.appendChild(tr);
      });
    }

    var top5 = ranked.slice(0, 5).map(function (e, i) { return { sq: e.sq, count: e.count, rank: i + 1 }; });
    var bot5 = ranked.slice(-5).map(function (e, i) { return { sq: e.sq, count: e.count, rank: ranked.length - 4 + i }; });
    fillTable(top5TbodyEl, top5);
    fillTable(bot5TbodyEl, bot5);
  }

  function rebuildLengthDropdown() {
    lengthSelect.innerHTML = '';
    sortedSqs.forEach(function (sq) {
      var entry  = DATA[sq];
      var total  = entry.byShape.reduce(function (s, a) { return s + a.length; }, 0);
      var nShapes= entry.shapes.length;
      var opt = document.createElement('option');
      opt.value = String(sq);
      opt.textContent = readableLen(sq) + ' — ' + total.toLocaleString() + ' segments · ' + nShapes + ' shape' + (nShapes === 1 ? '' : 's');
      lengthSelect.appendChild(opt);
    });
  }

  function updateChip() {
    var total = sortedSqs.reduce(function (s, sq) {
      return s + DATA[sq].byShape.reduce(function (t, a) { return t + a.length; }, 0);
    }, 0);
    chipEl.textContent = COLS + '×' + ROWS + ' · ' + total.toLocaleString() + ' total · ' + sortedSqs.length + ' lengths';
  }

  function bestSq() {
    return sortedSqs.reduce(function (best, sq) {
      var n  = DATA[sq].byShape.reduce(function (t, a) { return t + a.length; }, 0);
      var nb = DATA[best].byShape.reduce(function (t, a) { return t + a.length; }, 0);
      return n > nb ? sq : best;
    }, sortedSqs[0]);
  }

  function setGrid(n) {
    updateGridVars(n);
    rebuildLengthDropdown();
    updateChip();
    lengthSelect.value = String(bestSq());
    setLength(Number(lengthSelect.value));
  }

  function setLength(sq) {
    currentSq = sq;
    activeIdx = null; activeTileEl = null;
    renderTiles(); renderDetail(); renderStats();
  }

  function setMode(mode) {
    currentMode = mode;
    modeAllBtn.classList.toggle('active', mode === 'all');
    modeShapesBtn.classList.toggle('active', mode === 'shapes');
    randomBtn.disabled = mode === 'shapes';
    activeIdx = null; activeTileEl = null;
    renderTiles(); renderDetail();
  }

  function renderTiles() {
    var entry  = DATA[currentSq];
    var shapes = entry.shapes;
    var byShape= entry.byShape;
    tilesEl.innerHTML = '';
    activeTileEl = null;

    if (currentMode === 'all') {
      var total = byShape.reduce(function (s, a) { return s + a.length; }, 0);
      tilesHdrEl.textContent = total + ' segment' + (total === 1 ? '' : 's') + ' of length ' + readableLen(currentSq);
      flatSegs = [];
      shapes.forEach(function (shape, si) {
        var color = getColor(si);
        byShape[si].forEach(function (seg) {
          var idx = flatSegs.length;
          flatSegs.push({ shapeIdx: si, seg: seg });
          var svg = makeTileSvg(seg, color);
          (function (i, el) {
            el.addEventListener('click', function () { activate(i, el); });
          }(idx, svg));
          tilesEl.appendChild(svg);
        });
      });
    } else {
      var unusedCount = shapes.filter(function (_, si) { return byShape[si].length === 0; }).length;
      var hdr = shapes.length + ' shape' + (shapes.length === 1 ? '' : 's') + ' of length ' + readableLen(currentSq);
      if (unusedCount > 0) hdr += ' · ' + unusedCount + ' unused in this grid';
      tilesHdrEl.textContent = hdr;
      flatSegs = [];
      shapes.forEach(function (shape, si) {
        var svg = makeShapeTile(shape[0], shape[1], getColor(si));
        if (byShape[si].length === 0) svg.classList.add('unused');
        (function (i, el) {
          el.addEventListener('click', function () { activate(i, el); });
        }(si, svg));
        tilesEl.appendChild(svg);
      });
    }
  }

  function activate(i, el) {
    if (activeTileEl) activeTileEl.classList.remove('active');
    activeIdx = i; activeTileEl = el;
    if (el) el.classList.add('active');
    renderDetail();
  }

  function renderDetail() {
    detailBodyEl.innerHTML = '';

    if (activeIdx === null) {
      var p = document.createElement('p');
      p.className = 'd-empty';
      p.textContent = currentMode === 'shapes'
        ? 'Click a shape tile to inspect it.'
        : 'Click a tile or use Random to inspect a segment.';
      detailBodyEl.appendChild(p);
      return;
    }

    var entry  = DATA[currentSq];
    var shapes = entry.shapes;
    var byShape= entry.byShape;

    if (currentMode === 'all') {
      var item  = flatSegs[activeIdx];
      var seg   = item.seg;
      var si    = item.shapeIdx;
      var shape = shapes[si];
      var color = getColor(si);
      var dx = seg[2] - seg[0], dy = seg[3] - seg[1];

      appendEl('h2', {}, 'Segment detail');
      appendEl('p', { className: 'd-idx' }, '#' + (activeIdx + 1) + ' of ' + flatSegs.length);
      detailBodyEl.appendChild(makeDetailSvg(seg, color));

      var meta = document.createElement('div');
      meta.className = 'd-meta';
      meta.innerHTML =
        '<b>From</b> (' + seg[0] + ', ' + seg[1] + ') <b>to</b> (' + seg[2] + ', ' + seg[3] + ')<br>' +
        '<b>dx</b> ' + dx + ', <b>dy</b> ' + dy + '<br>' +
        '<b>Length</b> ' + readableLen(currentSq) + ' <small>(d² = ' + currentSq + ')</small><br>' +
        '<b>Shape</b> <span style="color:' + color + '">' + shapeName(shape[0], shape[1]) + '</span>';
      detailBodyEl.appendChild(meta);
      detailBodyEl.appendChild(makeNav(activeIdx, flatSegs.length));

    } else {
      var si    = activeIdx;
      var shape = shapes[si];
      var color = getColor(si);

      appendEl('h2', {}, 'Shape detail');
      appendEl('p', { className: 'd-idx' }, 'Shape ' + (si + 1) + ' of ' + shapes.length);
      detailBodyEl.appendChild(makeDetailShapeSvg(shape[0], shape[1], color));

      var meta = document.createElement('div');
      meta.className = 'd-meta';
      meta.innerHTML =
        '<b>Step vector</b> (' + shape[0] + ', ' + shape[1] + ')<br>' +
        '<b>Length</b> ' + readableLen(currentSq) + ' <small>(d² = ' + currentSq + ')</small><br>' +
        '<b>Placements</b> ' + byShape[si].length.toLocaleString() + ' on this grid';
      detailBodyEl.appendChild(meta);
      detailBodyEl.appendChild(makeNav(activeIdx, shapes.length));
    }
  }

  function makeNav(idx, total) {
    var nav = document.createElement('div');
    nav.className = 'd-nav';
    nav.appendChild(mkBtn('← Prev', idx === 0, function () {
      if (activeIdx > 0) {
        var tiles = tilesEl.querySelectorAll('svg.tile');
        activate(activeIdx - 1, tiles[activeIdx - 1] || null);
        if (tiles[activeIdx]) tiles[activeIdx].scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      }
    }));
    nav.appendChild(mkBtn('Next →', idx === total - 1, function () {
      if (activeIdx < total - 1) {
        var tiles = tilesEl.querySelectorAll('svg.tile');
        activate(activeIdx + 1, tiles[activeIdx + 1] || null);
        if (tiles[activeIdx]) tiles[activeIdx].scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      }
    }));
    return nav;
  }

  function mkBtn(label, disabled, handler) {
    var b = document.createElement('button');
    b.textContent = label; b.disabled = disabled;
    b.addEventListener('click', handler);
    return b;
  }

  function appendEl(tag, props, text) {
    var el = document.createElement(tag);
    Object.keys(props).forEach(function (k) { el[k] = props[k]; });
    if (text !== undefined) el.textContent = text;
    detailBodyEl.appendChild(el);
    return el;
  }

  Object.keys(ALL_DATA).map(Number).sort(function (a, b) { return a - b; }).forEach(function (n) {
    var opt = document.createElement('option');
    opt.value = String(n);
    opt.textContent = n + '×' + n;
    gridSelect.appendChild(opt);
  });
  gridSelect.value = '10';

  gridSelect.addEventListener('change', function () { setGrid(Number(gridSelect.value)); });
  lengthSelect.addEventListener('change', function () { setLength(Number(lengthSelect.value)); });
  modeAllBtn.addEventListener('click', function () { if (currentMode !== 'all') setMode('all'); });
  modeShapesBtn.addEventListener('click', function () { if (currentMode !== 'shapes') setMode('shapes'); });
  randomBtn.addEventListener('click', function () {
    var n = flatSegs.length;
    var i = Math.floor(Math.random() * n);
    var tiles = tilesEl.querySelectorAll('svg.tile');
    activate(i, tiles[i] || null);
    if (tiles[i]) tiles[i].scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  });

  setGrid(10);
}());
}
</script>
</body>
</html>
""".trimIndent()
