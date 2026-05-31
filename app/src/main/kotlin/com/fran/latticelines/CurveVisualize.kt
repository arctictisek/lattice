package com.fran.latticelines

import java.io.File

fun main() {
    val allData: Map<Int, Map<Int, Int>> = (2..30).associate { n ->
        n to countBySquaredLength(allSegments(gridPoints(n, n)))
    }
    File("curve.html").writeText(buildCurvePage(buildCurveJson(allData)))
    println("Generated: curve.html — open in a browser.")
}

private fun buildCurveJson(data: Map<Int, Map<Int, Int>>): String = buildString {
    append('{')
    data.entries.sortedBy { it.key }.forEachIndexed { i, (n, hist) ->
        if (i > 0) append(',')
        append('"').append(n).append("\":{")
        hist.entries.sortedBy { it.key }.forEachIndexed { j, (sq, count) ->
            if (j > 0) append(',')
            append('"').append(sq).append("\":").append(count)
        }
        append('}')
    }
    append('}')
}

private fun buildCurvePage(jsonData: String): String = """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Lattice Lines — Curve</title>
  <style>
    *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
    :root {
      color-scheme: dark;
      --blue: #3b82f6; --blue-d: #60a5fa;
      --bg: #0f172a; --surface: #1e293b; --border: #334155;
      --text: #f1f5f9; --muted: #94a3b8;
    }
    html, body { height: 100%; font-family: system-ui, -apple-system, sans-serif; font-size: 14px; color: var(--text); background: var(--bg); }
    body { display: flex; flex-direction: column; overflow: hidden; }

    header {
      display: flex; align-items: center; gap: 12px;
      padding: 0 20px; height: 52px;
      background: var(--surface); border-bottom: 1px solid var(--border); flex-shrink: 0;
    }
    h1 { font-size: 16px; font-weight: 600; letter-spacing: -.02em; white-space: nowrap; }
    .vr { width: 1px; height: 20px; background: var(--border); flex-shrink: 0; }
    .label { font-size: 13px; color: var(--muted); white-space: nowrap; }
    select {
      padding: 5px 10px; border: 1px solid var(--border); border-radius: 6px;
      font: inherit; background: var(--surface); cursor: pointer;
    }
    select:focus { outline: 2px solid var(--blue); outline-offset: 1px; border-color: transparent; }
    .chip { font-size: 12px; color: var(--muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

    #chart-wrap { flex: 1; padding: 20px; overflow: hidden; display: flex; }
    #chart-svg { width: 100%; height: 100%; display: block; }
    #chart-svg text { font-family: system-ui, -apple-system, sans-serif; }

    #tooltip {
      display: none; position: fixed; pointer-events: none;
      background: var(--surface); border: 1px solid var(--border);
      border-radius: 8px; padding: 8px 12px; font-size: 13px; line-height: 1.75;
      color: var(--text); white-space: nowrap; box-shadow: 0 4px 20px rgba(0,0,0,.6);
    }
    #tooltip b { color: var(--blue-d); }
    #tooltip .sub { color: var(--muted); font-size: 11px; }
  </style>
</head>
<body>
<header>
  <h1>Lattice Lines</h1>
  <span class="vr"></span>
  <span class="label">Grid size</span>
  <select id="size-select"></select>
  <span class="vr"></span>
  <span class="chip" id="chip"></span>
</header>
<div id="chart-wrap">
  <svg id="chart-svg" viewBox="0 0 900 450" preserveAspectRatio="none">
    <defs>
      <linearGradient id="curve-grad" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#3b82f6" stop-opacity="0.45"/>
        <stop offset="100%" stop-color="#3b82f6" stop-opacity="0.03"/>
      </linearGradient>
      <clipPath id="chart-clip">
        <rect x="70" y="25" width="810" height="375"/>
      </clipPath>
    </defs>

    <!-- Chart area background -->
    <rect x="70" y="25" width="810" height="375" fill="#0d1a2e" rx="4"/>

    <!-- Axis lines -->
    <line x1="70" y1="25" x2="70" y2="400" stroke="#334155" stroke-width="1"/>
    <line x1="70" y1="400" x2="880" y2="400" stroke="#334155" stroke-width="1"/>

    <!-- Axis labels -->
    <text x="16" y="213" text-anchor="middle" transform="rotate(-90 16 213)" fill="#475569" font-size="12">Segments</text>
    <text x="475" y="443" text-anchor="middle" fill="#475569" font-size="12">Length</text>

    <!-- Dynamic Y axis grid lines + tick labels -->
    <g id="y-axis-g"></g>
    <!-- Dynamic X axis ticks + labels -->
    <g id="x-axis-g"></g>

    <!-- Curve fill and line (clipped to chart area) -->
    <g clip-path="url(#chart-clip)">
      <path id="curve-fill" fill="url(#curve-grad)"/>
      <polyline id="curve-line" fill="none" stroke="#3b82f6" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/>
    </g>

    <!-- Hover indicator line -->
    <line id="indicator" stroke="#475569" stroke-width="1" stroke-dasharray="4 3" visibility="hidden"/>
    <!-- Hover active dot -->
    <circle id="active-dot" r="5" fill="#60a5fa" stroke="#0f172a" stroke-width="2" visibility="hidden"/>

    <!-- Transparent overlay captures all mouse events -->
    <rect id="overlay" x="70" y="25" width="810" height="375" fill="transparent" style="cursor:crosshair"/>
  </svg>
</div>
<div id="tooltip"></div>
<script>
(function () {
  var DATA = $jsonData;

  var VB_W = 900, VB_H = 450;
  var ML = 70, MR = 20, MT = 25, MB = 50;
  var CW = VB_W - ML - MR;   // 810
  var CH = VB_H - MT - MB;   // 375
  var BOTTOM = MT + CH;       // 400

  var SVG_NS = 'http://www.w3.org/2000/svg';
  function mkSvg(tag, attrs) {
    var el = document.createElementNS(SVG_NS, tag);
    Object.keys(attrs).forEach(function (k) { el.setAttribute(k, String(attrs[k])); });
    return el;
  }
  function mkText(x, y, content, fill, anchor, size) {
    var el = mkSvg('text', { x: x, y: y, fill: fill, 'text-anchor': anchor, 'font-size': size });
    el.textContent = content;
    return el;
  }

  function readableLen(sq) {
    var d = Math.sqrt(sq);
    return Math.round(d) === d ? String(Math.round(d)) : '√' + sq;
  }

  function niceInterval(maxVal, targetTicks) {
    var raw = maxVal / targetTicks;
    var mag = Math.pow(10, Math.floor(Math.log10(raw)));
    var steps = [1, 2, 5, 10];
    for (var i = 0; i < steps.length; i++) {
      if (steps[i] * mag >= raw) return steps[i] * mag;
    }
    return 10 * mag;
  }

  var sizes = Object.keys(DATA).map(Number).sort(function (a, b) { return a - b; });

  var svgEl   = document.getElementById('chart-svg');
  var pathEl  = document.getElementById('curve-fill');
  var lineEl  = document.getElementById('curve-line');
  var indEl   = document.getElementById('indicator');
  var dotEl   = document.getElementById('active-dot');
  var tipEl   = document.getElementById('tooltip');
  var selEl   = document.getElementById('size-select');
  var chipEl  = document.getElementById('chip');
  var yAxisG  = document.getElementById('y-axis-g');
  var xAxisG  = document.getElementById('x-axis-g');

  var currentEntries = [];

  function render(n) {
    var hist = DATA[n];
    var entries = Object.keys(hist).map(Number).sort(function (a, b) { return a - b; }).map(function (sq) {
      return { sq: sq, len: Math.sqrt(sq), count: hist[sq] };
    });

    var maxLen   = entries[entries.length - 1].len;
    var maxCount = entries.reduce(function (m, e) { return Math.max(m, e.count); }, 0);
    var total    = entries.reduce(function (s, e) { return s + e.count; }, 0);
    var winner   = entries.reduce(function (best, e) { return e.count > best.count ? e : best; }, entries[0]);

    var xMax = maxLen * 1.04;
    var yMax = maxCount * 1.1;

    function sx(len)   { return ML + (len / xMax) * CW; }
    function sy(count) { return MT + CH - (count / yMax) * CH; }

    entries.forEach(function (e) { e.sx = sx(e.len); e.sy = sy(e.count); });

    // ── Fill path ──
    var d = 'M ' + sx(0) + ' ' + BOTTOM;
    entries.forEach(function (e) { d += ' L ' + e.sx + ' ' + e.sy; });
    d += ' L ' + entries[entries.length - 1].sx + ' ' + BOTTOM + ' Z';
    pathEl.setAttribute('d', d);

    // ── Curve line ──
    lineEl.setAttribute('points', entries.map(function (e) { return e.sx + ',' + e.sy; }).join(' '));

    // ── Y axis ──
    yAxisG.innerHTML = '';
    var yInterval = niceInterval(maxCount, 5);
    for (var yv = yInterval; yv <= maxCount * 1.05; yv += yInterval) {
      var y = sy(yv);
      if (y < MT || y > BOTTOM) continue;
      yAxisG.appendChild(mkSvg('line', { x1: ML, y1: y, x2: ML + CW, y2: y, stroke: '#1a2f4a', 'stroke-width': 0.8 }));
      var lbl = yv >= 10000 ? Math.round(yv / 1000) + 'k' : yv >= 1000 ? (yv / 1000).toFixed(1) + 'k' : String(yv);
      yAxisG.appendChild(mkText(ML - 8, y + 4, lbl, '#64748b', 'end', 11));
    }

    // ── X axis ──
    xAxisG.innerHTML = '';
    var xInterval = maxLen < 3 ? 0.5 : maxLen < 6 ? 1 : maxLen < 15 ? 2 : 5;
    var xStart    = Math.ceil(1 / xInterval) * xInterval;
    var lastTick  = Math.floor(maxLen / xInterval) * xInterval;
    for (var xv = xStart; xv <= lastTick + 0.001; xv += xInterval) {
      var xpos = sx(xv);
      xAxisG.appendChild(mkSvg('line', { x1: xpos, y1: BOTTOM, x2: xpos, y2: BOTTOM + 5, stroke: '#64748b', 'stroke-width': 1 }));
      var tickLbl = Number.isInteger(xv) ? String(xv) : xv.toFixed(1);
      xAxisG.appendChild(mkText(xpos, BOTTOM + 18, tickLbl, '#64748b', 'middle', 11));
    }

    // ── Chip ──
    chipEl.textContent =
      n + '×' + n + ' · ' +
      total.toLocaleString() + ' total segments · peak: ' +
      readableLen(winner.sq) + ' (' + winner.count.toLocaleString() + ' segs)';

    currentEntries = entries;
  }

  // ── Hover ──────────────────────────────────────────────
  var overlay = document.getElementById('overlay');

  function svgCoords(e) {
    var r = svgEl.getBoundingClientRect();
    return { x: (e.clientX - r.left) / r.width * VB_W };
  }

  overlay.addEventListener('mousemove', function (e) {
    if (!currentEntries.length) return;
    var pos = svgCoords(e);
    var nearest = currentEntries.reduce(function (best, entry) {
      var d = Math.abs(entry.sx - pos.x);
      return d < best.d ? { d: d, entry: entry } : best;
    }, { d: Infinity, entry: null }).entry;
    if (!nearest) return;

    indEl.setAttribute('x1', nearest.sx); indEl.setAttribute('x2', nearest.sx);
    indEl.setAttribute('y1', MT);         indEl.setAttribute('y2', BOTTOM);
    indEl.setAttribute('visibility', 'visible');

    dotEl.setAttribute('cx', nearest.sx); dotEl.setAttribute('cy', nearest.sy);
    dotEl.setAttribute('visibility', 'visible');

    tipEl.style.display = 'block';
    tipEl.innerHTML =
      '<b>' + readableLen(nearest.sq) + '</b>' +
      ' <span class="sub">≈ ' + nearest.len.toFixed(3) + '</span><br>' +
      nearest.count.toLocaleString() + ' segments';

    var margin = 14, tw = tipEl.offsetWidth, th = tipEl.offsetHeight;
    var left = e.clientX + margin;
    if (left + tw > window.innerWidth - 8) left = e.clientX - tw - margin;
    tipEl.style.left = left + 'px';
    tipEl.style.top  = Math.max(8, e.clientY - th / 2) + 'px';
  });

  overlay.addEventListener('mouseleave', function () {
    indEl.setAttribute('visibility', 'hidden');
    dotEl.setAttribute('visibility', 'hidden');
    tipEl.style.display = 'none';
  });

  // ── Init ───────────────────────────────────────────────
  sizes.forEach(function (n) {
    var opt = document.createElement('option');
    opt.value = String(n);
    opt.textContent = n + '×' + n;
    selEl.appendChild(opt);
  });

  selEl.addEventListener('change', function () { render(Number(selEl.value)); });

  selEl.value = '30';
  render(30);
}());
</script>
</body>
</html>
""".trimIndent()
