/**
 *  File: HubitatPixelblazeDriver.groovy
 *  Platform: Hubitat
 *
 *  Allows hubitat to control a Pixelblaze addressable LED controller. 
 *  Information on the Pixelblaze is available at www.electromage.com.
 *
 *  Requirements:
 *    A Pixelblaze controller on the local LAN. 
 *
 *    Date        Ver     Who    What
 *    ----        ---     ---    ----
 *    2019-7-31   0.1     JEM    Created
 *    2019-7-31   0.11    JEM    added SwitchLevel capability, JSON bug fixes
 *    2019-12-19  1.0.0   JEM    lazy connection strategy, auto reconnect, 
 *                               auto pattern list refresh, new on/off method, more...
 *    2020-02-05  1.0.1   JEM    support for latest Pixelblaze firmware features
 *    2020-07-22  1.1.1   JEM    support for dividing strip into multiple segments 
 *    2020-12-05  1.1.3   JEM    Hubitat Package Manager support
 *    2021-02-02  2.0.1   JEM    v2 release: Color control/enhanced multisegment support
 *    2021-12-27  2.0.2   JEM    Expanded automation support & getVariable()/getVariableResult
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */

import hubitat.helper.InterfaceUtils
import hubitat.helper.HexUtils

/**
 * SECTION TAG: Constants and configuration data
 */
def version() {"2.0.2"}

def PORT() { ":81" }            // Pixelblaze's websocket port. Must include colon
def idleWaitTime() { 120}       // minimum seconds till connection goes idle
def defaultBrightness() { 50 }  // use this brightness if we can't determine device state.  
def maxSegments() { 12 }        // maximum number of segments allowed for multisegment setups
def segmentDescriptorSize() { 7 }
def waitForPixelblazeVars() { 100 }   // how long we give the Pixelblaze to respond to websocket requests (ms)

def UNKNOWN() { "(unknown)" } 
def EMPTYSTR() {""}
def WS_CONNECTED() { "connected" }
def WS_DISCONNECTED() { "not connected" }
def WS_WAITING() { "waiting" }

metadata {
    definition (name: "Pixelblaze Controller",
                namespace: "ZRanger1",
                author: "ZRanger1(JEM)",
                importUrl: "https://raw.githubusercontent.com/zranger1/hubitatpixelblazedriver/master/HubitatPixelblazeDriver.groovy") {
        capability "Actuator"
        capability "Switch"
        capability "SwitchLevel"
        capability "Initialize"
        capability "LightEffects"
        capability "ColorControl"
        
        command    "setSpeed", [[name: "Speed*", type: "NUMBER", description: "(0 to 100)"]]            
   
        
        command "setActivePattern", [[name: "Name*",type: "STRING"]]
        command "setVariables", ["STRING"]
        command "getControls"
        command "setControl", [[name: "Name*", type: "STRING"],
                               [name: "Values*", type: "STRING"],
                               [name: "Save", type: "STRING", description: "true/false"]]
        command "getPatterns"
        command "startSequencer"
        command "randomPattern"
        command "nextPattern"
        command "previousPattern"
        command "stopSequencer"
        command "setSequencerMode",[[name: "Mode*", type: "NUMBER", description: "(1 - Shuffle, 2 - Playlist)"]] 
        command "setSequenceTimer",[[name:"Seconds", type:"NUMBER"]]
        command "resetSegments"
        command "getVariable",[[name: "Name*", type: "STRING"]]        
        command "getVariables"
        command "setIPAddress",["STRING"]        

        attribute "activePattern","STRING"
        attribute "connectionStatus","STRING"         
        attribute "deviceName","STRING" 
        attribute "effectNumber","NUMBER"
        attribute "ipAddress","STRING"
        attribute "sequencerMode","NUMBER"                
        attribute "Speed", "NUMBER" 
        attribute "getVariableResult", "NUMBER"
    }
}

preferences {
    input("ip", "text", title: "IP Address", description: "IP address of Pixelblaze", required: true)

    input name: "multisegEnable", type: "bool", title: "Use multisegment pattern", defaultValue: false
    input name: "numSegments", type: "number", title: "Number of segments (1-12)", defaultValue: 4
   
    input name: "switchWithPatterns", type: "bool", title: "Use Patterns for On/Off", defaultValue : false   
    input("onPattern", "text", title: "On Pattern", description: "Name of pattern to use with \"On\" command", required : false)
    input("offPattern", "text", title: "Off Pattern", description: "Name of pattern to use with \"Off\" command", required : false)    
    
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false    
}

/**
 * SECTION TAG: Helper methods for logging
 */
def logsOff() {
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

def logDebug(String str) {
  if (logEnable) { log.debug(str) }
}

/**
 * SECTION TAG: Initialization 
 */ 
def installed(){
    log.info "Pixelblaze device handler v${version()} installed."       
    initialize()
}

def updated() {
    log.info "Pixelblaze handler updated. v${version()}."
    initialize()
}

def initialize() {
  logDebug("initialize")
    
// reset connection and any scheduled events
  pbCloseSocket()
  unschedule()
    
// initialize persistent device data    
  state.version = version()
  state.patternList = [ : ]

// initialize non-persistent device data   
  sendEvent([name: "connectionStatus", value: WS_DISCONNECTED()])
 
  device.updateDataValue("segments","0")
  device.updateDataValue("advancedMultiseg","0")
  device.updateDataValue("colorControlName",EMPTYSTR())
  device.updateDataValue("speedControlName",EMPTYSTR())
  device.updateDataValue("activePatternId",EMPTYSTR())
  device.updateDataValue("colorControlLevel",EMPTYSTR())
  device.updateDataValue("pixelCount","0")
  device.updateDataValue("lastPatternUpdate","0")
  device.updateDataValue("patternNeedsInit","0") 
  setLastNetCall()
    
// set device state values shown in UI. It not only looks
// better, but dashboard tiles and other integrations 
// need these values to work.  
  sendEvent([name: "activePattern", value: UNKNOWN()])
  sendEvent([name: "deviceName", value: EMPTYSTR()]) 
  sendEvent([name: "hue", value: 0])  
  sendEvent([name: "level", value: defaultBrightness()])  
  sendEvent([name: "saturation", value: 100])    
    
// save IP address in a place that's accessible to Maker API        
  sendEvent([name: "ipAddress", value: ip])        
    
// try to connect to device     
  connectToPixelblaze()
    
// schedule connection manager -- try to keep the Pixelblaze
// connected when the device is switched "on".  If it's off
// disconnect after a period of inactivity to improve Hub and
// PB performance, and to save LAN bandwidth.  (Check interval
// is set to 1/2 the specified idle wait time interval.)
  runInMillis(idleWaitTime() * 500,'connectionManager')  
}    

/**
 * SECTION TAG: Networking infrastructure - connect, disconnect, send, status...
 */
def setLastNetCall() {
    device.updateDataValue("lastSocketCall",Long.toString(now()))       
}

def getLastNetCall() {
    s = device.getDataValue("lastSocketCall")
    return Long.valueOf(s)
}

def setLastPatternUpdate() {
    device.updateDataValue("lastPatternUpdate",Long.toString(now()))       
}

def getLastPatternUpdate() {
    s = device.getDataValue("lastPatternUpdate")
    return Long.valueOf(s)
}

// attempt to open websocket connection to pixelblaze, and schedule data updater
// to run when the connection is successfully established
def connectToPixelblaze() {  
  if (ip == null) {
    logDebug("connectToPixelblaze: IP address not set.")
    return
  }
  
  if (isConnected()) return;  
  
  pbOpenSocket();
  runInMillis(300,'awaitConnection',[data: "initializePostConnection"])       
}

// retrieve all data that is relevant to the state of the currently 
// running pattern -- hardware config, variables, and controls
def getCurrentPatternState() {
  id = getDataValue("activePatternId")
  logDebug("getCurrentPatternState: (${id})")
  getVariables()
  getControls()
  getConfig()

}

// initialization to be done after websocket connection is
// established.
def initializePostConnection() {
    logDebug("initializePostConnection")
    
// update pattern list if it has been more than 10 minutes since
// we last did so.    
    last = getLastPatternUpdate()
    t = (now() - last) / 1000
    if (t >= 600) { 
        logDebug("initializePostConnection: Updating pattern list.")
        getPatterns()
    }   

// ask for updates to all other Pixelblaze status information    
    getCurrentPatternState()    
}

// returns true if websocket connection is currently active, false otherwise
def isConnected() {
  try {
    status = device.currentValue("connectionStatus")
    return status.startsWith(WS_CONNECTED())   
  }  
  catch(e) {
    // handles uninitialized connection status attribute
    return false
  }  
}

// true if connection has been requested, but is not
// yet open, false otherwise
def isWaitingToConnect() {
  try {
    status = device.currentValue("connectionStatus")  
    return (status.startsWith(WS_WAITING()))
  }  
  catch(e) {
    return true
  }   
}

// fake yield-while-waiting-for-connection 
def awaitConnection(targetMethod=null) {

// isWaiting will fail due to timeout or error,
// so we're not gonna be stuck here forever.  
  while (isWaitingToConnect()) {
    runIn(1,'awaitConnection',[data: targetMethod])
    return
  }
  
  if (isConnected()) {
    if (targetMethod != null)
      this."${targetMethod}"()   
  }
}
 
// Scheduled at intervals to disconnect if the Pixelblaze
// is switched "off" and we've gone several minutes with
// no user I/O.  If so, close the connection to minimize net traffic.  
def connectionManager() { 
  // stay connected while the PB is on, plus a few minutes
  // after that
  if (device.currentValue("state") != "on") {
    connectToPixelblaze() 
    return
  }
  // if the PB is off and we've been idle for a while, disconnect  
  last = getLastNetCall()
  t = (now() - last) / 1000
  if (isConnected() && (t >= idleWaitTime())) { 
    logDebug("Idle for ${t} seconds. Disconnecting.")
    pbCloseSocket()
  }    
  runInMillis(idleWaitTime() * 500,'DisconnectInactive')  
}

// open websocket connection to Pixelblaze
def pbOpenSocket() { 
    try {
      addr = (ip.split(":")[0]) + PORT()        
      sendEvent([name: "connectionStatus", value: WS_WAITING()])   
      interfaces.webSocket.connect("ws://${addr}")
      pauseExecution(250)
    }
    catch(e) {
      log.error("Exception in pbOpenSocket ",e) 
      sendEvent([name: "connectionStatus", value: WS_DISCONNECTED()])          
    }   
    setLastNetCall()  
}

def pbCloseSocket() {
    interfaces.webSocket.close()
}

// send json command to Pixelblaze.  If the connection is
// not currently established, opens it and requests 
// a state update from the Pixelblaze
def sendMsg(String s) {
    connectToPixelblaze()
    interfaces.webSocket.sendMessage(s)
    setLastNetCall()        
}

// TODO - special handling for "failure: Connection reset" state when
// the Pixelblaze gets restarted???
def webSocketStatus(String status) {
    logDebug("webSocketStatus: ${status}")
    
    if (status.startsWith("status: open")) {    
        sendEvent([name: "connectionStatus", value: WS_CONNECTED()]) 
    } 
    else if (status.startsWith("status: closing")) {
        sendEvent([name: "connectionStatus", value: WS_DISCONNECTED()])
    }    
    else if (status.startsWith("failure: Connection reset")) {
        sendEvent([name: "connectionStatus", value: WS_DISCONNECTED()])            
        runInMillis(250,'connectToPixelblaze')
    }
    else {
        sendEvent([name: "connectionStatus", value: WS_DISCONNECTED()])        
    }
}

/* 
  SECTION TAG: Multisegment support -- allows users to subdivide
  a single LED strip into multiple independent segments
*/

// When a multisegment pattern becomes added, updateSegments is
// called to ask all the child segment drivers to load their data
def updateSegments(Map vars) {     
  if (!multisegEnable) return
        
// Tell each segment to send its data to the pixelblaze and enable
  segList = getChildDevices()  
  for (i = 0; i < segList.size(); i++) { 
    segList[i].updateDriverState()  
  }

// clear pattern init flag if it is set.    
  device.updateDataValue("patternNeedsInit","0")            
}       

// retrieve settings for named segment from state database
// called by per-segment child drivers
def getSegmentData(Integer segNo) {
  return state.segmentMap[("z_${segNo}")]
}

// Save per-segment settings.
// if segmentation is enabled, and the specified segment is currently
// in range, set the stored data and send it to the pixelblaze
// called by per-segment child drivers.
def setSegmentData(Integer segNo,Map data) {
  if (multisegEnable && (segNo < device.getDataValue("segments").toInteger())) {
    name = "z_${segNo}"
    state.segmentMap[(name)] = data
  
// build array to send to pixelblaze  
    segArray = new float[segmentDescriptorSize()] 
    segArray[0] = data["state"]
    segArray[1] = data["hue"]        
    segArray[2] = data["sat"]
    segArray[3] = data["bri"]
    segArray[4] = data["effect"]
    segArray[5] = data["size"]
    segArray[6] = 1-data["speed"]+0.0005 // (1-n) to simplify speed calculations on Pixelblaze
      
    cmd = "{ \"${name}\" : ${segArray} }"   // JsonOutput.toJson([(name):segArray]) 
    setVariables(cmd)    
  } 
  else {
    logDebug("setSegmentData: not multisegment, or segment out of range.")
  }    
}   

// called from driver when the user changes the length of a segment 
// TODO - do we actually need this, or will the user work out
// the segment lengths regardless?
def adjustSegmentSizes() {
  ;
}

// helper function - delete all children
private removeChildDevices(kids) {
	kids.each {deleteChildDevice(it.deviceNetworkId)}
} 

/*
  SECTION TAG: Parser - methods for parsing packets from the pixelblaze
*/

// scan the active pattern's list of Web UI controls for
// the first color picker control.  If found, read its settings
// and save its name for later use
def findColorControl(Map ctl) {
  name = EMPTYSTR()
  hue = 0
  sat = 100
  bri = EMPTYSTR()
   
  for (x in ctl) {

      // handle hsv picker control -- just rescale the 0..1 values
      // to Hubitat's 0-100% range
      if (x.key.startsWith("hsvPicker")) { 
        name = x.key
        hue = Math.floor(100.0 * x.value[0])
        sat = Math.floor(100.0 * x.value[1])
        bri = x.value[2]
        break
      } 
      // handle rgb picker control - rescale the 0..1 values to
      // 0-255 RGB and convert to HSV.
      else if (x.key.startsWith("rgbPicker")) {
        name = x.key 
        x.value[0] = Math.floor(x.value[0] * 255.0)
        x.value[1] = Math.floor(x.value[1] * 255.0)
        x.value[2] = Math.floor(x.value[2] * 255.0)          
        hsv = hubitat.helper.ColorUtils.rgbToHSV(x.value)
        hue = hsv[0]
        sat = hsv[1]
        bri = hsv[2]
        break;
      }
  }
  device.updateDataValue("colorControlName",name)    
  sendEvent([name: "hue", value: hue])  
  sendEvent([name: "saturation", value: sat])  
  device.updateDataValue("colorControlLevel",bri.toString())   
}

// scan the active pattern's list of Web UI controls for
// the first "speed" control.  If found, read its settings
// and save its name for later use
def findSpeedControl(Map ctl) {
  name = EMPTYSTR()
  speed = 0
    
  for (x in ctl) {
    if (x.key.contains("Speed")) {            
//      logDebug("      ${x.key} : ${x.value}")
      name = x.key
      speed = Math.floor(x.value * 100)
      break
    }      
  }
  device.updateDataValue("speedControlName",name);  // now do something w/settings
  sendEvent([name: "effectSpeed", value: speed])              
}
        
// process configuration packets from the pixelblaze
// current brightness, LED count, software version #, etc. 
// for now, we grab brightness so we can later set it to zero
// to implement a "hard" off switch which preserves current pattern
// state, then restores it when the user turns the pixelblaze 
// back "on".   
def parseHardwareConfig(Map json) {

  if (json.containsKey("brightness")) {
    bri = Math.floor(json.brightness * 100)
    if ((bri > 0) && (bri != device.currentValue("level")))  {
      setLevel(bri)    
    } 
  }
  
  if (json.containsKey("name")) {
    sendEvent([name: "deviceName", value: json.name]) 
  }
  
  if (json.containsKey("pixelCount")) {
    device.updateDataValue("pixelCount",json.pixelCount.toString())
  }
                  
  if (json.containsKey("activeProgram")) {  
    name = patternNameFromId(json.activeProgram.activeProgramId) 
    lastId = device.getDataValue("activePatternId");      
    logDebug("    active pattern is ${name} : ${json.activeProgram.activeProgramId}")
    logDebug("    lastId is ${lastId}")
   
    // if the active pattern has changed, schedule an update for information
    // on variables, multiseg status, control settings...
    if (lastId != json.activeProgram.activeProgramId) {     
      device.updateDataValue("activePatternId",json.activeProgram.activeProgramId) 
      device.updateDataValue("patternNeedsInit","1")
      sendEvent([name: "activePattern", value: name]) 
      
      runInMillis(100,'getCurrentPatternState')
    }    
  }  
    
  if (json.containsKey("controls")) {
      logDebug("    parse: control list ${json.controls}") 
    ctl = json.controls.(device.getDataValue("activePatternId"))
    findColorControl(ctl)
    findSpeedControl(ctl)
  }          
}

// process json-ized list of exported variables from the pixelblaze.
def parseVariableList(Map json, String frame) {
  def i, status, segList
    
  if (json.containsKey("vars")) {
    logDebug("    parse: variable list ${json.vars}")  
      
// save complete variable list so getVariable can access it later.  
    logDebug("   parseVariableList frame: ${frame}")
    device.updateDataValue("varList",frame);      
      
// multisegment support is off unless the pattern explicitly turns
// it on.
    device.updateDataValue("segments","0")
    device.updateDataValue("advancedMultiseg","0")          

// does the pattern have multisegment support?  If so, update the
// table of segmentation settings.
    if (json.vars.containsKey("__ver")) {
      // Web UI version of advanced multiseg has __ver set to -1 so we 
      // can ignore it here.
      if (json.vars.__ver < 0) return;  
      // if using the new version of the multiseg pattern, we get the number
      // of segments from the hub device's user preferences
      device.updateDataValue("advancedMultiseg","1") 
      device.updateDataValue("segments",numSegments.toString())
      setVariables("{\"__n_segments\" : ${numSegments}}")
        
      // if the Pixelblaze has been rebooted, or the pattern reset by an outside device,
      // the pattern's internal __boot flag will be true.  If that's the case, we need
      // to trigger a segment data reload.
      if (json.vars.containsKey("__boot")) {
        if (json.vars.__boot != 0) {
          device.updateDataValue("patternNeedsInit","1");
        }
        setVariables("{\"__boot\" : 0}")
      }
      if (device.getDataValue("patternNeedsInit") == "1") {
        updateSegments()        
      }                    
    }
    // otherwise, for backward compatibility, get the number of segments from the
    // old version of the pattern, which gets the number of segments from the 
    // Pixelblaze
    else if (json.vars.containsKey("__n_segments")){          
      device.updateDataValue("segments",json.vars.__n_segments.toString()) 
    } 
  }  
}     

// handle json text frames
def parseJsonFrame(String frame) {  
  // check for web UI status bar update frame.  This means
  // that someone else is using the Pixelblaze. These things
  // come in at about 1hz, and for the moment, we just discard
  // them as quickly as possible.
  if (frame.startsWith('{"fps"')) {
     return
  }
            
//  logDebug("Received JSON frame: ${frame}")    
  json = null
   
  try {
    json = new groovy.json.JsonSlurper().parseText(frame)
    if (json == null){
      logDebug "parseJsonFrame: JsonSlurper returned null"
      return
    }     
    parseVariableList(json,frame)              
    parseHardwareConfig(json)
  }
  catch(e) {
    log.error("Exception while parsing json: ",e)
    log.error frame
    return
  }      
}

// handle binary (hex encoded) pattern list frames. Note that
// the pattern list can be spread across multiple frames
def parsePatternListFrame(String frame) {
  logDebug("parse: pattern list")

  // convert the data portion of the hex string to a normal string 
  def byte[] listFrame = hubitat.helper.HexUtils.hexStringToByteArray(frame)
  def rawList = new String(listFrame,2,listFrame.length - 2)
      
  // since program listings can be spread across multiple frames, we need to check
  // for start and end frame tags, which are in the second decoded byte      
  if (listFrame[1] & 0x01) {  //look for 0x01 start frame indicator
    logDebug "    start frame found"
    device.updateDataValue("tmpList","")
  }
    
   // pattern list can span multiple packets.  Add each new
   // packet to our temporary string accumulator
  pl = device.getDataValue("tmpList")
  pl = pl + rawList
  device.updateDataValue("tmpList",pl)
 
  // Look for 0x04 completion frame indicator, then process the
  // packet list into a sorted map.
  if (listFrame[1] & 0x04) { 
    logDebug("    end frame found")  
    // convert list entries into a map, sorted and indexed by pattern name
    newMap = pl.tokenize("\n").collectEntries(new TreeMap<>(String.CASE_INSENSITIVE_ORDER)) {
      it.tokenize("\t").with {[(it[1]):it[0]]}
    }

  // use the sorted TreeMap to build a "human readable" list for the lightEffects atribute, and 
  // append the sort index to our original map because the state database won't store a sorted map.
  // we get around this by saving the sort order so we can look it up (somewhat slowly) later.   
    eff = [ : ]
    newMap.eachWithIndex{entry, i -> entry.value = [entry.value,i]; eff[i] = entry.key }  
      
  // save the updated map to the state database and update lightEffects
    state.patternList = newMap   
    sendEvent(name:"lightEffects",value: eff)            
    device.removeDataValue("tmpList")    

    logDebug "parse: ${state.patternList.size()} patterns loaded"  
  }    
}

/**
 * parse data from Pixelblaze's websocket interface
 * NOTE - when you open the Pixelblaze's web UI in a browser,
 * it generates a fairly constant stream of packets, which need to 
 * be ignored as rapidly as possible so we don't bog down the Hubitat.
 */ 
def parse(String frame) {
  if (frame.startsWith("{")) {
    parseJsonFrame(frame)  
  } 
  else if (frame.startsWith("07")) {
    parsePatternListFrame(frame)  
  } 
  else {
    // other event or random binary frame.  
    // Ignore for now
    ;
  }
}

/**
 * SECTION TAG: Command handlers 
 */
 
// remove current children and replace them according to the
// current number of segments reported by the pixelblaze.
// NOTE: Don't press this button until you're actually connected
// to your pixelblaze and have the proper pattern running.
def resetSegments() {
  def i,lstring,segs
    
  connectToPixelblaze()  
  if(!isConnected()) {
    logDebug("resetSegments: Must be connected for this operation")
    return     
  }
          
  logDebug("resetSegments: Removing all child devices")
  removeChildDevices(getChildDevices())
  
  if (!multisegEnable) return

  logDebug("resetSegments: Building segment descriptor table")
          
// Build a segment description for all possible segments
  segMap = [ : ]
  for (i = 0; i < maxSegments(); i++) {
    segData = [ : ]
  
    segData["state"] = 1
    segData["hue"] = 0.0752
    segData["sat"] = 0.7562
    segData["bri"] = 0.3
    segData["effect"] = 0
    segData["size"] = 1
    segData["speed"] = 0.5

    segMap[("z_${i}")] = segData
  }  
  state.segmentMap = segMap
//  logDebug("$segMap")  
  
// create a child device for each active segment  
  segs = device.getDataValue("segments").toInteger()    
  logDebug("resetSegments: Creating ${segs} child devices")   
  
  for (i = 0; i < segs; i++) {
    lstring = device.getLabel() + " Segment " + i.toString()      
    addChildDevice("Pixelblaze Segment", "${device.id}-${i}",
       	    		[label: lstring, 
                  	 isComponent: false, 
                     name: "Pixelblaze Segment ${i}"])    
  }
}
 
// retrieves hardware configuration info. 
def getConfig() {
    sendMsg("{ \"getConfig\" : true }")    
}

// Given the name of a pattern, return the
// pattern's ID string and sort index, or NULL if not found. 
def getPatternId(String name) {
    return state.patternList[name]
}    

// retrieve list of Pixelblaze Web UI controls and settings for
// the currently active pattern if available.
def getControls() {
  id = device.getDataValue("activePatternId")
  len = id ? id.length() : 0;
  if (len < 1) {
    logDebug("getControls: Active pattern not available.")
  } else {
    sendMsg("{ \"getControls\" : ${id} }")  
  }
}

// sets values for the specified Web UI control in the current pattern.
def setControl(String ctl_name, String values, String saveFlash="false") {
  str = "{\"setControls\":{\"${ctl_name}\" : ${values}}, \"save\": ${saveFlash}}"
  logDebug(str)        
  sendMsg(str);
}

// n should be in the range 0-100
def setGlobalBrightness(BigDecimal n) {
    if (n > 0) {n = n / 100.0} // avoid weirdness around floating point zero
    sendMsg("{ \"brightness\" : ${n}, \"save\": false }" )
}
 
def setActivePattern(name) {
   def pid = getPatternId(name)
   if (pid) {    
     def str = "{ \"activeProgramId\" : \"${pid[0]}\", \"save\": false }"
     sendMsg(str)  
     sendEvent([name: "activePattern", value: name]) 
     sendEvent([name:"effectName", value:name])               
     sendEvent([name:"effectNumber", value:pid[1]])     
   }
}

// Set exported pattern variables on the pixelblaze.
// Takes a json-formatted string of the names and values
// of variables exported in the currently active pattern.
// see readme.md and the Pixelblaze expression documentation
// for details.
def setVariables(String jsonString) {
//   logDebug "setVariables(${jsonString})"
    def str = "{ \"setVars\" : ${jsonString}}"
    sendMsg(str)
}

def setColor(hsv) {   
  logDebug("setColor(${hsv})")   
    
  ctl = device.getDataValue("colorControlName")
  if (ctl == "") {
    logDebug("setColor: no color control available.")
    return
  }
  isHSV = ctl.startsWith("hsv")
  pbColor = new Double[3];        
    
// convert to 0..1 range list for Pixelblaze 
    if (isHSV) {   // handle HSV color picker  
      logDebug("setColor: HSV")
      pbColor[0] =(hsv.hue > 0) ? hsv.hue / 100.0 : 0;
      pbColor[1] =(hsv.saturation > 0) ? hsv.saturation / 100.0 : 0;    
      pbColor[2] = (hsv.level > 0) ? hsv.level / 100.0 : 0; 
    } 
    else {  // handle RGB color picker
      try {
        logDebug("setColor: RGB")
        rgb = hubitat.helper.ColorUtils.hsvToRGB([hsv.hue, hsv.saturation, hsv.level])
        pbColor[0] = rgb[0] / 255.0
        pbColor[1] = rgb[1] / 255.0
        pbColor[2] = rgb[2] / 255.0                  
      }
      catch (e) {
        logDebug("Attempt to set RGB color w/NaN value - ignoring.")
        return
      }        
    }
       
    setControl(ctl,pbColor.toString(),"false")       
}

def getDeviceColor() {
  hsv = [hue: device.currentValue("hue"),
         saturation: device.currentValue("saturation"),
         level: device.getDataValue("colorControlLevel").toInteger() ]
        
  return hsv
}

def setHue(value) {
    hsv = getDeviceColor()
    setColor([hue: value, saturation: hsv.saturation, level: hsv.level])
}

def setSaturation(value) {
    hsv = getDeviceColor()
    setColor([hue: hsv.hue, saturation: value, level: hsv.level])
}

// set brightness
// NOTE - duration is not currently supported
// TODO - support duration (potentially for on/off too.)
//  maybe can make a generic engine for changing level over time.
def setLevel(BigDecimal level,BigDecimal duration=0) {
    setGlobalBrightness(level)
    if (level > 0) {  
      sendEvent([name: "level", value: level])      
      sendEvent([name: "switch", value: "on"])   
    }
}

// request a json-ized list of exported variables from
// the currently active pattern.
def getVariables() {
    sendMsg("{ \"getVars\" : true }")
}

// retrieves requested variable value after async websocket
// query has returned (if all is working)
def getVariableWorker(String varName) {
  // get most recently saved list and look for the value we want. 
  try {
    String frame = device.getDataValue("varList");      
    json = new groovy.json.JsonSlurper().parseText(frame)
      
    if (json == null){
      logDebug "getVariable: JsonSlurper returned null"
    }
    else if (json?.vars?.containsKey(varName)) {
      val = json.vars[varName];        
    } 
  }
  catch(e) {
    log.error("Exception while parsing json: ",e)
    log.error frame
  }  
    
  logDebug "getVariable ${varName} returned: ${val}"     
  sendEvent([name: "getVariableResult", value: val, isStateChange: false])    
}

// retrieve the (numerical) value of a single export variable
// from the active pattern and save it in the getVariableResult 
// attribute
def getVariable(String varName) {
  String frame;
    
  // make sure the variable list is as up to date as possible
  getVariables()
  val = null;
   
  // elaborate way of faking Thread.sleep().  Why, hubitat, why?  
  runInMillis(waitForPixelblazeVars(),'getVariableWorker',[data : varName]);
  pauseExecution(waitForPixelblazeVars() + 20);
}

// Update the list of pattern names and IDs
def getPatterns() {
    setLastPatternUpdate()
    sendMsg("{ \"listPrograms\" : true }")  
    pauseExecution(100);
}

def on() {       
    if (switchWithPatterns) {
      setActivePattern(onPattern)
    }
    else {
      bri = device.currentValue("level")     
      if (bri <= 0) bri = defaultBrightness()
      setGlobalBrightness(bri)      
    }
    getCurrentPatternState() 
    sendEvent([name: "switch", value: "on"])       
}

def off() {   
    if (switchWithPatterns) {
      setActivePattern(offPattern)
    }
    else {
      setGlobalBrightness(0)
    }
    sendEvent([name: "switch", value: "off"])      
}

def startSequencer() {
  on()  
  mode = device.currentValue("sequencerMode")
  sendMsg("{\"sequencerMode\": ${mode}, \"runSequencer\" : true }") 
}

def stopSequencer() {
    sendMsg("{\"sequencerMode\": 0, \"runSequencer\" : false }")   
}

// does not affect a running sequencer.  Will take effect the next
// time the sequencer is started.
def setSequencerMode(BigDecimal mode) {
  if ((mode < 1) || (mode > 2)) {
      logDebug("setSequencerMode: Invalid mode specified")
      mode = 1;
  }
  sendEvent([name: "sequencerMode", value: mode])     
}

def setSequenceTimer(BigDecimal n) {
    sendMsg("{ \"sequenceTimer\" : ${n} }" )
}

def randomPattern() {
  rnd = new Random()
  name = (state.patternList?.keySet() as List)[rnd.nextInt(state.patternList.size())] 
  if (name) {
    setActivePattern(name)
  }
  else {
    logDebug("randomPattern: no pattern list")
  }   
}

def indexFromPatternName(pattern) {
  if (pattern == UNKNOWN())  return 0  
  return state.patternList[pattern][1]  
}

def patternNameFromId(id) {
  name = null
 
  try {   
    if (state.patternList && id) {
      name = state.patternList.find { it.value[0] == id }.key
    }  
  }
  catch (e) {
    logDebug("patternNameFromId: id not found")
  }
    
  return (name) ? name : UNKNOWN() 
}

def patternNameFromIndex(int n) {
  name = null
 
  try {   
    if (state.patternList) {
      name = state.patternList.find { it.value[1] == n }.key
    }  
  }
  catch (e) {
    logDebug("patternNameFromIndex: index not found")
  }
    
  return (name) ? name : UNKNOWN() 
}

def nextPattern() {
  i = indexFromPatternName(device.currentValue("activePattern"))
  if (i < 0) return // not connected, or no pattern list yet
  
  i++
  if (i >= state.patternList.size()) i = 0
  setActivePattern(patternNameFromIndex(i))  
}
 
def previousPattern() {
  i = indexFromPatternName(device.currentValue("activePattern"))
  if (i < 0) return // pattern not found
  
  i--
  if (i < 0) i = state.patternList.size() - 1
  setActivePattern(patternNameFromIndex(i))  
 }
 
// LightEffects commands
def  setEffect(effectNo) {
  logDebug("setEffect(${effectNo})")
  n = patternNameFromIndex((int) effectNo);
  if (n != null) {
    setActivePattern(n);
  }
}

def setNextEffect() {
  nextPattern();
}
      
def setPreviousEffect() {
  previousPattern();
}

def setSpeed(BigDecimal speed) {
  logDebug("setSpeed(${speed})")
  ctl = device.getDataValue("speedControlName")
  if (ctl == null) {
    logDebug("setSpeed: no speed control available.")
    return
  }
      
  spd = (speed > 0) ? speed / 100.0 : 0;
  setControl(ctl,spd.toString(),"false")    
  sendEvent([name: "Speed", value: speed])   
} 

// maker API can use this command to set IP address, enabling mDNS
// resolution of Pixelblaze by name on remote system.
def setIPAddress(String addr) {
    logDebug("setIPAdress(${addr})")   
    device.updateSetting("ip",addr)
    sendEvent([name: "ipAddress", value: addr])  
}  