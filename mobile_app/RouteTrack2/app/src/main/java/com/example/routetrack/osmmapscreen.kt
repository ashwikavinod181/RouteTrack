@file:Suppress("NewApi")

package com.example.routetrack

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

// ============================================================================
// 1. STUDENT MAP SCREEN
// ============================================================================

@SuppressLint("SetJavaScriptEnabled", "NewApi")
@Composable
fun OSMMapScreen(
    studentLat: Double,
    studentLon: Double,
    busLat: Double?,
    busLon: Double?,
    busName: String,
    busEta: String,
    busStatus: String,
    onBack: () -> Unit
) {
    val html = remember(studentLat, studentLon, busLat, busLon, busEta, busStatus) {
        buildStudentMapHtml(studentLat, studentLon, busLat, busLon, busName, busEta, busStatus)
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    @SuppressLint("SetJavaScriptEnabled")
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.setSupportZoom(false)
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false
                    webViewClient = WebViewClient()
                    loadDataWithBaseURL(
                        "https://map.routetrack.local", html,
                        "text/html", "UTF-8", null
                    )
                    webViewRef = this
                }
            },
            update = { wv ->
                wv.loadDataWithBaseURL(
                    "https://map.routetrack.local", html,
                    "text/html", "UTF-8", null
                )
            },
            modifier = Modifier.fillMaxSize()
        )

        // Back button overlay
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart)
                .background(Color.White, RoundedCornerShape(50))
                .size(48.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = RouteTrackColors.DarkGreen
            )
        }

        // Recenter FAB
        FloatingActionButton(
            onClick = { webViewRef?.evaluateJavascript("recenterStudent();", null) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 200.dp),
            containerColor = Color.White,
            contentColor = RouteTrackColors.PrimaryGreen,
            elevation = FloatingActionButtonDefaults.elevation(6.dp)
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "My Location")
        }
    }
}

// ============================================================================
// 2. DRIVER MAP SCREEN
// ============================================================================

@SuppressLint("SetJavaScriptEnabled", "NewApi", "MissingPermission")
@Composable
fun DriverOSMMapScreen(
    driverLat: Double,
    driverLon: Double,
    isBroadcasting: Boolean,
    onBack: () -> Unit
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(driverLat, driverLon) {
        webViewRef?.evaluateJavascript(
            "updateDriverPosition($driverLat, $driverLon, $isBroadcasting);", null
        )
    }

    val html = remember { buildDriverMapHtml(driverLat, driverLon, isBroadcasting) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    @SuppressLint("SetJavaScriptEnabled")
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.setSupportZoom(false)
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false
                    webViewClient = WebViewClient()
                    loadDataWithBaseURL(
                        "https://map.routetrack.local", html,
                        "text/html", "UTF-8", null
                    )
                    webViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Back button
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart)
                .background(Color.White, RoundedCornerShape(50))
                .size(48.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = RouteTrackColors.DarkGreen
            )
        }

        // Broadcasting badge
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isBroadcasting) RouteTrackColors.PrimaryGreen
                else RouteTrackColors.Warning
            ),
            elevation = CardDefaults.cardElevation(6.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isBroadcasting) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color.White, RoundedCornerShape(50))
                    )
                }
                Text(
                    text = if (isBroadcasting) "BROADCASTING LIVE" else "NOT BROADCASTING",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Recenter FAB
        FloatingActionButton(
            onClick = { webViewRef?.evaluateJavascript("recenterDriver();", null) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 160.dp),
            containerColor = Color.White,
            contentColor = RouteTrackColors.PrimaryGreen,
            elevation = FloatingActionButtonDefaults.elevation(6.dp)
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "Centre on me")
        }
    }
}

// ============================================================================
// 3. STUDENT MAP HTML
// ============================================================================

fun buildStudentMapHtml(
    studentLat: Double,
    studentLon: Double,
    busLat: Double?,
    busLon: Double?,
    busName: String,
    busEta: String,
    busStatus: String
): String {
    val hasBus      = busLat != null && busLon != null
    val busLatStr   = busLat?.toString() ?: "0"
    val busLonStr   = busLon?.toString() ?: "0"
    val safeName    = busName.replace("'", "\\'").replace("\"", "&quot;")
    val isLive      = busStatus.uppercase() == "LIVE"
    val statusColor = if (isLive) "#16a34a" else "#d97706"
    val statusBg    = if (isLive) "#dcfce7" else "#fef3c7"
    val statusDot   = if (isLive) "#22c55e" else "#f59e0b"

    return """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no"/>
<style>
*{margin:0;padding:0;box-sizing:border-box;-webkit-tap-highlight-color:transparent;}
html,body{height:100%;width:100%;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;overflow:hidden;background:#e5e7eb;}
#map{position:absolute;inset:0;}
canvas{display:block;cursor:grab;}
canvas.dragging{cursor:grabbing;}

/* ── Top header bar ── */
#topbar{
  position:absolute;top:0;left:0;right:0;z-index:900;
  background:linear-gradient(180deg,rgba(0,0,0,0.35) 0%,transparent 100%);
  height:80px;pointer-events:none;
}

/* ── Bottom sheet ── */
#sheet{
  position:absolute;bottom:0;left:0;right:0;z-index:1000;
  background:#ffffff;
  border-radius:20px 20px 0 0;
  box-shadow:0 -2px 20px rgba(0,0,0,0.18);
  padding:0 0 32px;
  transform:translateY(0);
  transition:transform .3s cubic-bezier(.4,0,.2,1);
}
#sheet-handle{
  width:36px;height:4px;background:#d1d5db;border-radius:4px;
  margin:12px auto 0;cursor:pointer;
}
#sheet-body{padding:16px 20px 0;}
#sheet-title{
  font-size:11px;font-weight:700;color:#9ca3af;
  letter-spacing:1px;text-transform:uppercase;
  margin-bottom:14px;
  display:flex;align-items:center;gap:6px;
}
#sheet-title::before{
  content:'';display:inline-block;width:8px;height:8px;
  border-radius:50%;background:$statusDot;
  ${if (isLive) "animation:pulse-dot 1.5s ease-in-out infinite;" else ""}
}
@keyframes pulse-dot{0%,100%{transform:scale(1);opacity:1;}50%{transform:scale(1.4);opacity:.7;}}

#info-cards{display:flex;gap:10px;}
.icard{
  flex:1;background:#f9fafb;border-radius:14px;
  padding:14px 12px;text-align:center;
  border:1px solid #f3f4f6;
  transition:background .2s;
}
.icard:active{background:#f0fdf4;}
.icard-label{
  font-size:10px;font-weight:700;color:#9ca3af;
  text-transform:uppercase;letter-spacing:.6px;
  margin-bottom:6px;
}
.icard-value{
  font-size:15px;font-weight:800;color:#111827;
  white-space:nowrap;overflow:hidden;text-overflow:ellipsis;
}
.icard-value.status-pill{
  display:inline-block;
  background:$statusBg;color:$statusColor;
  border-radius:20px;padding:2px 10px;
  font-size:13px;
}

/* ── Zoom controls ── */
#zoom-wrap{
  position:absolute;right:14px;z-index:1000;
  display:flex;flex-direction:column;
  gap:2px;bottom:185px;
}
.zbtn{
  width:44px;height:44px;background:white;
  border:none;border-radius:0;
  font-size:22px;font-weight:500;color:#374151;
  cursor:pointer;display:flex;align-items:center;justify-content:center;
  box-shadow:0 1px 4px rgba(0,0,0,0.22);
  transition:background .15s;
  user-select:none;
}
.zbtn:first-child{border-radius:8px 8px 0 0;}
.zbtn:last-child{border-radius:0 0 8px 8px;}
.zbtn:active{background:#f3f4f6;}
#zoom-divider{height:1px;background:#e5e7eb;width:44px;}

/* ── Scale bar ── */
#scalebar{
  position:absolute;left:14px;bottom:185px;z-index:1000;
  display:flex;flex-direction:column;align-items:flex-start;gap:3px;
}
#scale-line{height:4px;background:#374151;border-radius:2px;min-width:40px;max-width:120px;border:1px solid #374151;border-top:none;}
#scale-text{font-size:11px;font-weight:600;color:#374151;background:rgba(255,255,255,.85);padding:1px 4px;border-radius:3px;}

/* ── Loading overlay ── */
#loading{
  position:absolute;inset:0;z-index:800;
  background:rgba(255,255,255,0.85);
  display:flex;flex-direction:column;align-items:center;justify-content:center;gap:14px;
  transition:opacity .4s;
}
#loading.hidden{opacity:0;pointer-events:none;}
.spinner{
  width:44px;height:44px;border:4px solid #e5e7eb;
  border-top:4px solid #16a34a;border-radius:50%;
  animation:spin .7s linear infinite;
}
@keyframes spin{to{transform:rotate(360deg);}}
#loading p{font-size:14px;font-weight:600;color:#374151;}

/* ── Attribution ── */
#attrib{
  position:absolute;bottom:155px;right:4px;z-index:900;
  font-size:9px;color:#6b7280;background:rgba(255,255,255,.75);
  padding:2px 5px;border-radius:3px;
}
</style>
</head>
<body>
<div id="map"><canvas id="c"></canvas></div>
<div id="topbar"></div>
<div id="loading"><div class="spinner"></div><p>Loading map…</p></div>

<!-- Bottom sheet -->
<div id="sheet">
  <div id="sheet-handle"></div>
  <div id="sheet-body">
    <div id="sheet-title">Live Bus Tracking</div>
    <div id="info-cards">
      <div class="icard">
        <div class="icard-label">Route</div>
        <div class="icard-value" title="${safeName.ifEmpty { "N/A" }}">${safeName.ifEmpty { "N/A" }}</div>
      </div>
      <div class="icard">
        <div class="icard-label">ETA</div>
        <div class="icard-value">$busEta</div>
      </div>
      <div class="icard">
        <div class="icard-label">Status</div>
        <div class="icard-value"><span class="status-pill">$busStatus</span></div>
      </div>
    </div>
  </div>
</div>

<!-- Zoom -->
<div id="zoom-wrap">
  <button class="zbtn" id="btnZoomIn" onclick="zoomIn()">+</button>
  <div id="zoom-divider"></div>
  <button class="zbtn" id="btnZoomOut" onclick="zoomOut()">−</button>
</div>

<!-- Scale bar -->
<div id="scalebar">
  <div id="scale-text">…</div>
  <div id="scale-line"></div>
</div>

<div id="attrib">© OpenStreetMap contributors</div>

<script>
// ── State ──────────────────────────────────────────────────────────────────
var STUDENT = {lat:$studentLat, lon:$studentLon};
var BUS     = ${if (hasBus) "{lat:$busLatStr,lon:$busLonStr}" else "null"};
var zoom    = 15;
var center  = {lat:$studentLat, lon:$studentLon};
var W = 0, H = 0;
var tileCache = {};
var loadingEl = document.getElementById('loading');
var canvas    = document.getElementById('c');
var ctx       = canvas.getContext('2d');
var tilesNeeded = 0, tilesReady = 0;

// ── Auto-fit if both markers ───────────────────────────────────────────────
${if (hasBus) """
(function(){
  var lats=[STUDENT.lat,BUS.lat], lons=[STUDENT.lon,BUS.lon];
  center.lat=(Math.max.apply(null,lats)+Math.min.apply(null,lats))/2;
  center.lon=(Math.max.apply(null,lons)+Math.min.apply(null,lons))/2;
  var span=Math.max(
    Math.max.apply(null,lats)-Math.min.apply(null,lats),
    Math.max.apply(null,lons)-Math.min.apply(null,lons)
  );
  zoom=span>0.08?12:span>0.04?13:span>0.015?14:span>0.005?15:16;
})();
""" else "center={lat:STUDENT.lat,lon:STUDENT.lon};zoom=16;"}

// ── Projection helpers ─────────────────────────────────────────────────────
function mercY(lat){
  var s=Math.sin(lat*Math.PI/180);
  return (0.5-Math.log((1+s)/(1-s))/(4*Math.PI));
}
function tileCount(){return Math.pow(2,zoom);}

function latlon2tile(lat,lon){
  var n=tileCount();
  return {x:(lon+180)/360*n, y:mercY(lat)*n};
}

function latlon2px(lat,lon){
  var n=tileCount();
  var ct=latlon2tile(center.lat,center.lon);
  var pt=latlon2tile(lat,lon);
  return {x:W/2+(pt.x-ct.x)*256, y:H/2+(pt.y-ct.y)*256};
}

function px2latlon(x,y){
  var n=tileCount();
  var ct=latlon2tile(center.lat,center.lon);
  var tx=ct.x+(x-W/2)/256;
  var ty=ct.y+(y-H/2)/256;
  var lon=tx/n*360-180;
  var lat=Math.atan(Math.sinh(Math.PI*(1-2*ty/n)))*180/Math.PI;
  return {lat:lat,lon:lon};
}

// ── Tile management ────────────────────────────────────────────────────────
function getTile(tx,ty,z){
  var wtx=((tx%Math.pow(2,z))+Math.pow(2,z))%Math.pow(2,z);
  var key=z+'/'+wtx+'/'+ty;
  if(tileCache[key]) return tileCache[key]==='loading'?null:tileCache[key];
  tileCache[key]='loading';
  var img=new Image();
  img.crossOrigin='anonymous';
  var sub=['a','b','c'][(Math.abs(wtx)+Math.abs(ty))%3];
  img.src='https://'+sub+'.tile.openstreetmap.org/'+z+'/'+wtx+'/'+ty+'.png';
  img.onload=function(){tileCache[key]=img;render();};
  img.onerror=function(){tileCache[key]='err';render();};
  return null;
}

// ── Scale bar ─────────────────────────────────────────────────────────────
function updateScaleBar(){
  var mpp=156543.03392*Math.cos(center.lat*Math.PI/180)/tileCount();
  var targetPx=100;
  var meters=mpp*targetPx;
  var label,px;
  if(meters>=1000){var km=meters/1000;var nice=[1,2,5,10,20,50,100,200,500,1000];var n=nice.reduce(function(p,c){return Math.abs(c-km)<Math.abs(p-km)?c:p;});label=n+' km';px=n*1000/mpp;}
  else{var nice2=[5,10,20,50,100,200,500];var n2=nice2.reduce(function(p,c){return Math.abs(c-meters)<Math.abs(p-meters)?c:p;});label=n2+' m';px=n2/mpp;}
  document.getElementById('scale-text').textContent=label;
  document.getElementById('scale-line').style.width=Math.round(px)+'px';
}

// ── Draw helpers ──────────────────────────────────────────────────────────
function drawShadow(x,y,r){
  var g=ctx.createRadialGradient(x,y+r*0.8,0,x,y+r*0.8,r*1.6);
  g.addColorStop(0,'rgba(0,0,0,0.25)');g.addColorStop(1,'rgba(0,0,0,0)');
  ctx.save();ctx.beginPath();ctx.ellipse(x,y+r*0.9,r*1.2,r*0.5,0,0,2*Math.PI);
  ctx.fillStyle=g;ctx.fill();ctx.restore();
}

function drawYouMarker(x,y){
  // Accuracy ring (outer glow)
  ctx.save();
  ctx.beginPath();ctx.arc(x,y,24,0,2*Math.PI);
  ctx.fillStyle='rgba(59,130,246,0.12)';ctx.fill();
  ctx.restore();
  // White border
  ctx.save();
  ctx.beginPath();ctx.arc(x,y,13,0,2*Math.PI);
  ctx.fillStyle='white';ctx.fill();
  ctx.restore();
  // Blue dot
  ctx.save();
  ctx.shadowColor='rgba(59,130,246,0.5)';ctx.shadowBlur=8;
  ctx.beginPath();ctx.arc(x,y,10,0,2*Math.PI);
  ctx.fillStyle='#2563eb';ctx.fill();
  ctx.restore();
  // Inner white center
  ctx.save();
  ctx.beginPath();ctx.arc(x,y,3.5,0,2*Math.PI);
  ctx.fillStyle='white';ctx.fill();
  ctx.restore();
}

function drawBusMarker(x,y){
  var r=20, pinH=40;
  drawShadow(x,y+pinH*0.55,r);
  // Pin body
  ctx.save();
  ctx.shadowColor='rgba(22,163,74,0.45)';ctx.shadowBlur=14;
  // Teardrop shape
  ctx.beginPath();
  ctx.arc(x,y-pinH*0.5,r,Math.PI*0.85,Math.PI*0.15,false);
  ctx.quadraticCurveTo(x+r*0.6,y-pinH*0.1,x,y);
  ctx.quadraticCurveTo(x-r*0.6,y-pinH*0.1,x,y);
  ctx.closePath();
  var pg=ctx.createLinearGradient(x-r,y-pinH,x+r,y);
  pg.addColorStop(0,'#22c55e');pg.addColorStop(1,'#15803d');
  ctx.fillStyle=pg;ctx.fill();
  ctx.shadowBlur=0;
  // White inner circle
  ctx.beginPath();ctx.arc(x,y-pinH*0.5,r*0.65,0,2*Math.PI);
  ctx.fillStyle='rgba(255,255,255,0.95)';ctx.fill();
  ctx.restore();
  // Bus emoji
  ctx.save();
  ctx.font='bold 16px sans-serif';
  ctx.textAlign='center';ctx.textBaseline='middle';
  ctx.fillText('\uD83D\uDE8C',x,y-pinH*0.5);
  ctx.restore();
  // Label chip below pin
  var label='BUS';
  ctx.save();
  ctx.font='bold 10px sans-serif';
  var tw=ctx.measureText(label).width;
  var chipW=tw+14,chipH=18,chipX=x-chipW/2,chipY=y+6;
  ctx.beginPath();
  ctx.roundRect(chipX,chipY,chipW,chipH,9);
  ctx.fillStyle='#15803d';ctx.fill();
  ctx.fillStyle='white';
  ctx.textAlign='center';ctx.textBaseline='middle';
  ctx.fillText(label,x,chipY+chipH/2);
  ctx.restore();
}

function drawDashedLine(x1,y1,x2,y2){
  ctx.save();
  ctx.setLineDash([10,7]);
  ctx.lineDashOffset=0;
  ctx.strokeStyle='#16a34a';ctx.lineWidth=3;
  ctx.lineCap='round';ctx.globalAlpha=0.65;
  ctx.beginPath();ctx.moveTo(x1,y1);ctx.lineTo(x2,y2);
  ctx.stroke();
  ctx.restore();
}

// ── Main render ────────────────────────────────────────────────────────────
var frameReq=null;
function render(){
  if(frameReq) return;
  frameReq=requestAnimationFrame(function(){
    frameReq=null;
    _render();
  });
}

function _render(){
  if(!W||!H){resize();return;}
  ctx.clearRect(0,0,W,H);
  ctx.fillStyle='#e8eaed';ctx.fillRect(0,0,W,H);

  var n=tileCount();
  var ct=latlon2tile(center.lat,center.lon);
  var stx=Math.floor(ct.x-W/512);
  var sty=Math.floor(ct.y-H/512);
  var etx=Math.ceil(ct.x+W/512);
  var ety=Math.ceil(ct.y+H/512);

  tilesNeeded=0;tilesReady=0;
  for(var tx=stx;tx<=etx;tx++){
    for(var ty=sty;ty<=ety;ty++){
      if(ty<0||ty>=n)continue;
      tilesNeeded++;
      var img=getTile(tx,ty,zoom);
      if(img&&img!=='err'){
        ctx.drawImage(img,
          Math.round(W/2+(tx-ct.x)*256),
          Math.round(H/2+(ty-ct.y)*256),
          256,256);
        tilesReady++;
      }
    }
  }

  if(tilesReady>0) loadingEl.classList.add('hidden');

  // Dashed route line
  ${if (hasBus) """
  var bp=latlon2px(BUS.lat,BUS.lon);
  var sp=latlon2px(STUDENT.lat,STUDENT.lon);
  drawDashedLine(bp.x,bp.y-38,sp.x,sp.y);
  """ else ""}

  // Draw markers
  var sp2=latlon2px(STUDENT.lat,STUDENT.lon);
  drawYouMarker(sp2.x,sp2.y);

  ${if (hasBus) """
  var bp2=latlon2px(BUS.lat,BUS.lon);
  drawBusMarker(bp2.x,bp2.y);
  """ else ""}

  updateScaleBar();
}

// ── Resize ────────────────────────────────────────────────────────────────
function resize(){
  W=canvas.width=window.innerWidth;
  H=canvas.height=window.innerHeight;
  _render();
}
window.addEventListener('resize',resize);

// ── Smooth zoom with animation ─────────────────────────────────────────────
var zoomAnim={active:false,from:zoom,to:zoom,start:0,dur:200};

function animZoom(toZ,pivotX,pivotY){
  toZ=Math.max(3,Math.min(19,toZ));
  if(toZ===zoom)return;
  // Adjust center so pivot point stays fixed
  if(pivotX!==undefined&&pivotY!==undefined){
    var ll=px2latlon(pivotX,pivotY);
    var scale=Math.pow(2,toZ-zoom);
    // new center so that ll stays at pivotX,pivotY
    var ct=latlon2tile(center.lat,center.lon);
    var pt=latlon2tile(ll.lat,ll.lon);
    var newCTx=pt.x+(ct.x-pt.x)/scale;
    var newCTy=pt.y+(ct.y-pt.y)/scale;
    var n2=Math.pow(2,toZ);
    center.lon=newCTx/n2*360-180;
    var lat2=Math.atan(Math.sinh(Math.PI*(1-2*newCTy/n2)))*180/Math.PI;
    center.lat=lat2;
  }
  zoom=toZ;
  tileCache={};
  _render();
}

function zoomIn(){animZoom(zoom+1,W/2,H/2);}
function zoomOut(){animZoom(zoom-1,W/2,H/2);}
function recenterStudent(){
  center.lat=STUDENT.lat;center.lon=STUDENT.lon;zoom=16;tileCache={};_render();
}

// ── Touch & mouse drag ─────────────────────────────────────────────────────
var drag={active:false,sx:0,sy:0,slat:0,slon:0,moved:false};

function startDrag(x,y){
  drag={active:true,sx:x,sy:y,slat:center.lat,slon:center.lon,moved:false};
  canvas.classList.add('dragging');
}
function moveDrag(x,y){
  if(!drag.active)return;
  var dx=x-drag.sx,dy=y-drag.sy;
  if(Math.abs(dx)+Math.abs(dy)>3) drag.moved=true;
  var n=tileCount();
  var mpp=1/(n*256);
  center.lon=drag.slon-dx*mpp*360;
  var slat_merc=mercY(drag.slat);
  var new_merc=slat_merc+dy*mpp;
  new_merc=Math.max(0.001,Math.min(0.999,new_merc));
  center.lat=Math.atan(Math.sinh(Math.PI*(1-2*new_merc)))*180/Math.PI;
  render();
}
function endDrag(){drag.active=false;canvas.classList.remove('dragging');}

canvas.addEventListener('mousedown',function(e){startDrag(e.clientX,e.clientY);});
window.addEventListener('mousemove',function(e){moveDrag(e.clientX,e.clientY);});
window.addEventListener('mouseup',endDrag);

canvas.addEventListener('touchstart',function(e){
  e.preventDefault();
  if(e.touches.length===1) startDrag(e.touches[0].clientX,e.touches[0].clientY);
},{passive:false});
canvas.addEventListener('touchmove',function(e){
  e.preventDefault();
  if(e.touches.length===1) moveDrag(e.touches[0].clientX,e.touches[0].clientY);
  else if(e.touches.length===2) handlePinch(e);
},{passive:false});
canvas.addEventListener('touchend',function(e){
  endDrag();
  pinch.active=false;
});

// ── Pinch zoom (FIXED — persists zoom level correctly) ─────────────────────
var pinch={active:false,dist:0,cx:0,cy:0,startZoom:zoom};
function handlePinch(e){
  var t0=e.touches[0],t1=e.touches[1];
  var dx=t0.clientX-t1.clientX,dy=t0.clientY-t1.clientY;
  var d=Math.sqrt(dx*dx+dy*dy);
  var mx=(t0.clientX+t1.clientX)/2,my=(t0.clientY+t1.clientY)/2;
  if(!pinch.active){
    pinch.active=true;pinch.dist=d;pinch.cx=mx;pinch.cy=my;
    pinch.startZoom=zoom;
    endDrag();
    return;
  }
  // Continuous ratio-based zoom — no threshold jumping
  var ratio=d/pinch.dist;
  var newZoom=Math.round(pinch.startZoom+Math.log2(ratio));
  newZoom=Math.max(3,Math.min(19,newZoom));
  if(newZoom!==zoom){
    animZoom(newZoom,pinch.cx,pinch.cy);
    // Don't reset pinch.dist — keep relative to original start
  }
}

// ── Double-tap to zoom ────────────────────────────────────────────────────
var lastTap=0;
canvas.addEventListener('touchend',function(e){
  var now=Date.now();
  if(now-lastTap<300&&e.changedTouches.length===1){
    var t=e.changedTouches[0];
    animZoom(zoom+1,t.clientX,t.clientY);
  }
  lastTap=now;
});

// ── Mouse wheel zoom ──────────────────────────────────────────────────────
canvas.addEventListener('wheel',function(e){
  e.preventDefault();
  var delta=e.deltaY>0?-1:1;
  animZoom(zoom+delta,e.clientX,e.clientY);
},{passive:false});

// ── Boot ─────────────────────────────────────────────────────────────────
resize();
</script>
</body>
</html>"""
}

// ============================================================================
// 4. DRIVER MAP HTML
// ============================================================================

fun buildDriverMapHtml(
    driverLat: Double,
    driverLon: Double,
    isBroadcasting: Boolean
): String {
    val markerColor  = if (isBroadcasting) "#16a34a" else "#d97706"
    val markerGrad1  = if (isBroadcasting) "#22c55e" else "#f59e0b"
    val markerGrad2  = if (isBroadcasting) "#15803d" else "#b45309"

    return """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no"/>
<style>
*{margin:0;padding:0;box-sizing:border-box;-webkit-tap-highlight-color:transparent;}
html,body{height:100%;width:100%;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;overflow:hidden;background:#e8eaed;}
#map{position:absolute;inset:0;}
canvas{display:block;cursor:grab;}
canvas.dragging{cursor:grabbing;}

/* ── Zoom controls ── */
#zoom-wrap{
  position:absolute;right:14px;bottom:145px;z-index:1000;
  display:flex;flex-direction:column;gap:2px;
}
.zbtn{
  width:44px;height:44px;background:white;border:none;
  font-size:22px;font-weight:500;color:#374151;cursor:pointer;
  display:flex;align-items:center;justify-content:center;
  box-shadow:0 1px 4px rgba(0,0,0,0.22);
  transition:background .15s;user-select:none;
}
.zbtn:first-child{border-radius:8px 8px 0 0;}
.zbtn:last-child{border-radius:0 0 8px 8px;}
.zbtn:active{background:#f3f4f6;}
#zoom-divider{height:1px;background:#e5e7eb;width:44px;}

/* ── Coords bottom card ── */
#coords-card{
  position:absolute;bottom:0;left:0;right:0;z-index:1000;
  background:white;border-radius:20px 20px 0 0;
  padding:0 0 32px;
  box-shadow:0 -2px 20px rgba(0,0,0,0.18);
}
#handle{width:36px;height:4px;background:#d1d5db;border-radius:4px;margin:12px auto 0;}
#coords-body{padding:16px 20px 0;}
#coords-row{display:flex;gap:10px;}
.ci{
  flex:1;background:#f9fafb;border-radius:14px;
  padding:14px 10px;text-align:center;
  border:1px solid #f3f4f6;
}
.cl{font-size:10px;font-weight:700;color:#9ca3af;text-transform:uppercase;letter-spacing:.5px;margin-bottom:6px;}
.cv{font-size:13px;font-weight:800;color:#111827;}
.cv.live{color:#16a34a;}
.cv.idle{color:#d97706;}

/* ── Scale bar ── */
#scalebar{
  position:absolute;left:14px;bottom:145px;z-index:1000;
  display:flex;flex-direction:column;align-items:flex-start;gap:3px;
}
#scale-line{height:4px;background:#374151;border-radius:2px;border:1px solid #374151;border-top:none;min-width:40px;}
#scale-text{font-size:11px;font-weight:600;color:#374151;background:rgba(255,255,255,.85);padding:1px 4px;border-radius:3px;}

/* ── Loading ── */
#loading{
  position:absolute;inset:0;z-index:800;background:rgba(255,255,255,0.85);
  display:flex;flex-direction:column;align-items:center;justify-content:center;gap:14px;
  transition:opacity .4s;
}
#loading.hidden{opacity:0;pointer-events:none;}
.spinner{width:44px;height:44px;border:4px solid #e5e7eb;border-top:4px solid $markerColor;border-radius:50%;animation:spin .7s linear infinite;}
@keyframes spin{to{transform:rotate(360deg);}}
#loading p{font-size:14px;font-weight:600;color:#374151;}

/* ── Broadcast pulse animation ── */
@keyframes ripple{0%{transform:scale(1);opacity:.6;}100%{transform:scale(2.8);opacity:0;}}
.ripple{
  position:absolute;border-radius:50%;
  border:2px solid $markerColor;
  animation:ripple 1.8s ease-out infinite;
  pointer-events:none;
}

#attrib{position:absolute;bottom:125px;right:4px;z-index:900;font-size:9px;color:#6b7280;background:rgba(255,255,255,.75);padding:2px 5px;border-radius:3px;}
</style>
</head>
<body>
<div id="map"><canvas id="c"></canvas></div>
<div id="loading"><div class="spinner"></div><p>Loading map…</p></div>

<div id="zoom-wrap">
  <button class="zbtn" onclick="zoomIn()">+</button>
  <div id="zoom-divider"></div>
  <button class="zbtn" onclick="zoomOut()">−</button>
</div>

<div id="scalebar">
  <div id="scale-text">…</div>
  <div id="scale-line"></div>
</div>

<div id="coords-card">
  <div id="handle"></div>
  <div id="coords-body">
    <div id="coords-row">
      <div class="ci">
        <div class="cl">Latitude</div>
        <div class="cv" id="lat-val">${String.format("%.6f", driverLat)}</div>
      </div>
      <div class="ci">
        <div class="cl">Longitude</div>
        <div class="cv" id="lon-val">${String.format("%.6f", driverLon)}</div>
      </div>
      <div class="ci">
        <div class="cl">Status</div>
        <div class="cv ${if (isBroadcasting) "live" else "idle"}" id="status-val">${if (isBroadcasting) "LIVE" else "IDLE"}</div>
      </div>
    </div>
  </div>
</div>

<div id="attrib">© OpenStreetMap contributors</div>

<script>
var DRIVER   = {lat:$driverLat,lon:$driverLon};
var isLive   = $isBroadcasting;
var mColor   = '$markerColor';
var mGrad1   = '$markerGrad1';
var mGrad2   = '$markerGrad2';
var zoom     = 16;
var center   = {lat:$driverLat,lon:$driverLon};
var W=0,H=0;
var tileCache={};
var canvas   = document.getElementById('c');
var ctx      = canvas.getContext('2d');
var loadingEl= document.getElementById('loading');
var pulseAngle=0,animRunning=false;

// ── Projection ─────────────────────────────────────────────────────────────
function mercY(lat){var s=Math.sin(lat*Math.PI/180);return(0.5-Math.log((1+s)/(1-s))/(4*Math.PI));}
function tileCount(){return Math.pow(2,zoom);}
function latlon2tile(lat,lon){var n=tileCount();return{x:(lon+180)/360*n,y:mercY(lat)*n};}
function latlon2px(lat,lon){
  var ct=latlon2tile(center.lat,center.lon);
  var pt=latlon2tile(lat,lon);
  return{x:W/2+(pt.x-ct.x)*256,y:H/2+(pt.y-ct.y)*256};
}
function px2latlon(x,y){
  var n=tileCount();
  var ct=latlon2tile(center.lat,center.lon);
  var tx=ct.x+(x-W/2)/256,ty=ct.y+(y-H/2)/256;
  return{lat:Math.atan(Math.sinh(Math.PI*(1-2*ty/n)))*180/Math.PI,lon:tx/n*360-180};
}

// ── Tiles ──────────────────────────────────────────────────────────────────
function getTile(tx,ty,z){
  var n=Math.pow(2,z);
  var wtx=((tx%n)+n)%n;
  var key=z+'/'+wtx+'/'+ty;
  if(tileCache[key]) return tileCache[key]==='loading'?null:tileCache[key];
  tileCache[key]='loading';
  var img=new Image();img.crossOrigin='anonymous';
  var sub=['a','b','c'][(Math.abs(wtx)+Math.abs(ty))%3];
  img.src='https://'+sub+'.tile.openstreetmap.org/'+z+'/'+wtx+'/'+ty+'.png';
  img.onload=function(){tileCache[key]=img;render();};
  img.onerror=function(){tileCache[key]='err';render();};
  return null;
}

// ── Scale bar ──────────────────────────────────────────────────────────────
function updateScaleBar(){
  var mpp=156543.03392*Math.cos(center.lat*Math.PI/180)/tileCount();
  var meters=mpp*100;
  var label,px;
  if(meters>=1000){var nice=[1,2,5,10,20,50,100];var km=meters/1000;var n=nice.reduce(function(p,c){return Math.abs(c-km)<Math.abs(p-km)?c:p;});label=n+'km';px=n*1000/mpp;}
  else{var nice2=[5,10,20,50,100,200,500];var n2=nice2.reduce(function(p,c){return Math.abs(c-meters)<Math.abs(p-meters)?c:p;});label=n2+'m';px=n2/mpp;}
  document.getElementById('scale-text').textContent=label;
  document.getElementById('scale-line').style.width=Math.round(px)+'px';
}

// ── Draw driver marker ─────────────────────────────────────────────────────
function drawDriverMarker(x,y){
  var r=22,pinH=44;

  // Shadow ellipse
  var sg=ctx.createRadialGradient(x,y+4,0,x,y+4,r*1.5);
  sg.addColorStop(0,'rgba(0,0,0,0.28)');sg.addColorStop(1,'rgba(0,0,0,0)');
  ctx.save();ctx.beginPath();
  ctx.ellipse(x,y+pinH*0.45,r*1.1,r*0.4,0,0,2*Math.PI);
  ctx.fillStyle=sg;ctx.fill();ctx.restore();

  // Live pulse rings
  if(isLive){
    [0,0.6,1.2].forEach(function(offset,i){
      var a=(pulseAngle+offset)%(2*Math.PI);
      var scale=1+Math.sin(a)*0.3;
      var alpha=0.3-Math.sin(a)*0.2;
      ctx.save();
      ctx.beginPath();ctx.arc(x,y-pinH*0.45,r*scale,0,2*Math.PI);
      ctx.strokeStyle=mColor;ctx.lineWidth=2;ctx.globalAlpha=alpha;
      ctx.stroke();ctx.restore();
    });
  }

  // Teardrop pin
  ctx.save();
  ctx.shadowColor=mColor+'88';ctx.shadowBlur=16;
  ctx.beginPath();
  ctx.arc(x,y-pinH*0.45,r,Math.PI*0.8,Math.PI*0.2,false);
  ctx.quadraticCurveTo(x+r*0.7,y-pinH*0.08,x,y+2);
  ctx.quadraticCurveTo(x-r*0.7,y-pinH*0.08,x,y+2);
  ctx.closePath();
  var pg=ctx.createLinearGradient(x-r,y-pinH,x+r,y);
  pg.addColorStop(0,mGrad1);pg.addColorStop(1,mGrad2);
  ctx.fillStyle=pg;ctx.fill();
  ctx.shadowBlur=0;
  // White inner circle
  ctx.beginPath();ctx.arc(x,y-pinH*0.45,r*0.62,0,2*Math.PI);
  ctx.fillStyle='rgba(255,255,255,0.95)';ctx.fill();
  ctx.restore();

  // Bus icon
  ctx.save();
  ctx.font='bold 17px sans-serif';
  ctx.textAlign='center';ctx.textBaseline='middle';
  ctx.fillText('\uD83D\uDE8C',x,y-pinH*0.45);
  ctx.restore();

  // Status chip
  var statusLabel=isLive?'LIVE':'IDLE';
  ctx.save();
  ctx.font='bold 10px sans-serif';
  var tw=ctx.measureText(statusLabel).width;
  var cw=tw+14,ch=18,cx2=x-cw/2,cy2=y+8;
  ctx.beginPath();
  if(ctx.roundRect) ctx.roundRect(cx2,cy2,cw,ch,9);
  else ctx.rect(cx2,cy2,cw,ch);
  ctx.fillStyle=mColor;ctx.fill();
  ctx.fillStyle='white';ctx.textAlign='center';ctx.textBaseline='middle';
  ctx.fillText(statusLabel,x,cy2+ch/2);
  ctx.restore();
}

// ── Render ─────────────────────────────────────────────────────────────────
var frameReq=null;
function render(){
  if(frameReq)return;
  frameReq=requestAnimationFrame(function(){frameReq=null;_render();});
}
function _render(){
  if(!W||!H){resize();return;}
  ctx.clearRect(0,0,W,H);
  ctx.fillStyle='#e8eaed';ctx.fillRect(0,0,W,H);

  var n=tileCount();
  var ct=latlon2tile(center.lat,center.lon);
  var stx=Math.floor(ct.x-W/512),sty=Math.floor(ct.y-H/512);
  var etx=Math.ceil(ct.x+W/512),ety=Math.ceil(ct.y+H/512);
  var loaded=0;
  for(var tx=stx;tx<=etx;tx++){
    for(var ty=sty;ty<=ety;ty++){
      if(ty<0||ty>=n)continue;
      var img=getTile(tx,ty,zoom);
      if(img&&img!=='err'){
        ctx.drawImage(img,Math.round(W/2+(tx-ct.x)*256),Math.round(H/2+(ty-ct.y)*256),256,256);
        loaded++;
      }
    }
  }
  if(loaded>0) loadingEl.classList.add('hidden');

  updateScaleBar();
  var dp=latlon2px(DRIVER.lat,DRIVER.lon);
  drawDriverMarker(dp.x,dp.y);

  if(isLive){
    pulseAngle+=0.05;
    frameReq=requestAnimationFrame(function(){frameReq=null;_render();});
  }
}

// ── External update (called from Kotlin via evaluateJavascript) ────────────
function updateDriverPosition(lat,lon,live){
  DRIVER.lat=lat;DRIVER.lon=lon;isLive=live;
  mColor=live?'#16a34a':'#d97706';
  mGrad1=live?'#22c55e':'#f59e0b';
  mGrad2=live?'#15803d':'#b45309';
  center.lat=lat;center.lon=lon;
  tileCache={};
  document.getElementById('lat-val').textContent=lat.toFixed(6);
  document.getElementById('lon-val').textContent=lon.toFixed(6);
  var sv=document.getElementById('status-val');
  sv.textContent=live?'LIVE':'IDLE';
  sv.className='cv '+(live?'live':'idle');
  if(!isLive) _render();
}

function recenterDriver(){center.lat=DRIVER.lat;center.lon=DRIVER.lon;zoom=16;tileCache={};_render();}

// ── Resize ─────────────────────────────────────────────────────────────────
function resize(){W=canvas.width=window.innerWidth;H=canvas.height=window.innerHeight;_render();}
window.addEventListener('resize',resize);

// ── Zoom ───────────────────────────────────────────────────────────────────
function animZoom(toZ,pivotX,pivotY){
  toZ=Math.max(3,Math.min(19,toZ));
  if(toZ===zoom)return;
  if(pivotX!==undefined&&pivotY!==undefined){
    var ll=px2latlon(pivotX,pivotY);
    var scale=Math.pow(2,toZ-zoom);
    var ct=latlon2tile(center.lat,center.lon);
    var pt=latlon2tile(ll.lat,ll.lon);
    var ncx=pt.x+(ct.x-pt.x)/scale;
    var ncy=pt.y+(ct.y-pt.y)/scale;
    var n2=Math.pow(2,toZ);
    center.lon=ncx/n2*360-180;
    center.lat=Math.atan(Math.sinh(Math.PI*(1-2*ncy/n2)))*180/Math.PI;
  }
  zoom=toZ;tileCache={};_render();
}
function zoomIn(){animZoom(zoom+1,W/2,H/2);}
function zoomOut(){animZoom(zoom-1,W/2,H/2);}

// ── Drag ───────────────────────────────────────────────────────────────────
var drag={active:false,sx:0,sy:0,slat:0,slon:0};
function startDrag(x,y){drag={active:true,sx:x,sy:y,slat:center.lat,slon:center.lon};canvas.classList.add('dragging');}
function moveDrag(x,y){
  if(!drag.active)return;
  var dx=x-drag.sx,dy=y-drag.sy;
  var mpp=1/(tileCount()*256);
  center.lon=drag.slon-dx*mpp*360;
  var nm=mercY(drag.slat)+dy*mpp;
  nm=Math.max(0.001,Math.min(0.999,nm));
  center.lat=Math.atan(Math.sinh(Math.PI*(1-2*nm)))*180/Math.PI;
  render();
}
function endDrag(){drag.active=false;canvas.classList.remove('dragging');}
canvas.addEventListener('mousedown',function(e){startDrag(e.clientX,e.clientY);});
window.addEventListener('mousemove',function(e){moveDrag(e.clientX,e.clientY);});
window.addEventListener('mouseup',endDrag);
canvas.addEventListener('wheel',function(e){e.preventDefault();animZoom(zoom+(e.deltaY>0?-1:1),e.clientX,e.clientY);},{passive:false});

// ── Touch ──────────────────────────────────────────────────────────────────
var pinch={active:false,dist:0,startZoom:zoom};
canvas.addEventListener('touchstart',function(e){
  e.preventDefault();
  if(e.touches.length===1) startDrag(e.touches[0].clientX,e.touches[0].clientY);
  else if(e.touches.length===2){
    endDrag();
    var dx=e.touches[0].clientX-e.touches[1].clientX,dy=e.touches[0].clientY-e.touches[1].clientY;
    pinch={active:true,dist:Math.sqrt(dx*dx+dy*dy),startZoom:zoom};
  }
},{passive:false});
canvas.addEventListener('touchmove',function(e){
  e.preventDefault();
  if(e.touches.length===1&&!pinch.active) moveDrag(e.touches[0].clientX,e.touches[0].clientY);
  else if(e.touches.length===2&&pinch.active){
    var dx=e.touches[0].clientX-e.touches[1].clientX,dy=e.touches[0].clientY-e.touches[1].clientY;
    var d=Math.sqrt(dx*dx+dy*dy);
    var mx=(e.touches[0].clientX+e.touches[1].clientX)/2,my=(e.touches[0].clientY+e.touches[1].clientY)/2;
    var nz=Math.round(pinch.startZoom+Math.log2(d/pinch.dist));
    animZoom(nz,mx,my);
  }
},{passive:false});
canvas.addEventListener('touchend',function(e){endDrag();if(e.touches.length<2)pinch.active=false;});
var lastTap=0;
canvas.addEventListener('touchend',function(e){
  var now=Date.now();
  if(now-lastTap<300&&e.changedTouches.length===1){var t=e.changedTouches[0];animZoom(zoom+1,t.clientX,t.clientY);}
  lastTap=now;
});

// ── Boot ───────────────────────────────────────────────────────────────────
resize();
</script>
</body>
</html>"""
}