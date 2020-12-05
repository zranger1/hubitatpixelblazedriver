/**
 *
 *  File: HubitatPixelblazeDriver.groovy
 *  Platform: Hubitat
 *
 *  Allows hubitat to control a Pixelblaze addressable LED controller. 
 *  Information on the Pixelblaze is available at www.electromage.com.
 *  Note that I have nothing at all to do with the hardware.  I just
 *  like the device.  It's fun!
 *
 *  Requirements:
 *    A Pixelblaze controller on the local LAN. 
 *
 *    Date        Ver           Who       What
 *    ----        ---           ---       ----
 *    2019-7-31   0.1           JEM       Created
 *    2019-7-31   0.11          JEM       added SwitchLevel capability, JSON bug fixes
 *    2019-12-19  1.0.0         JEM       lazy connection strategy, auto reconnect, 
 *                                        auto pattern list refresh, new on/off method, more...
 *    2020-02-05  1.0.1         JEM       support for latest Pixelblaze firmware features
 *    2020-07-22  1.1.1         JEM       support for dividing strip into multiple segments 
 *    2020-12-05  1.1.3         JEM       Hubitat Package Manager support
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
 * Constants and configuration data
 */
def version() {"1.1.3"}

def idleWaitTime() { 120 } // seconds till connection goes idle
def defaultBrightness() { 50 } // use this brightness if we can't determine device state.  

def PATTERN_NOT_SET() { "(unknown)" } 
def PATTERN_SEQUENCER_RUNNING() { "(sequencer)" }

def WS_CONNECTED() { "connected" }
def WS_DISCONNECTED() { "not connected" }
def WS_WAITING() { "waiting" }

metadata {
    definition (name: "Pixelblaze Controller",
                namespace: "ZRanger1",
                author: "ZRanger1(JEM)",
                importUrl: "https://raw.githubusercontent.com/zranger1/hubitatpixelblazedriver/master/HubitatPixelblazeDriver.groovy") {
        capability "Switch"
        capability "SwitchLevel"
        capability "Initialize"

        command "setActivePattern", ["string"]
        command "setVariables", ["string"]
        command "getPatterns"
        command "startSequencer"
        command "randomPattern"
        command "nextPattern"
        command "previousPattern"
        command "stopSequencer"
        command "setSequenceTimer",["number"]
        command "resetChildren"
        command "getVariables"

        attribute "connectionStatus","string"
        attribute "activePattern","string"
    }
}

preferences {
    input("ip", "text", title: "IP Address", description: "IP address of Pixelblaze, including port #", required: true)
    input name: "switchWithPatterns", type: "bool", title: "Use Patterns for On/Off", defaultValue : false   
    input("onPattern", "text", title: "On Pattern", description: "Name of pattern to use with \"On\" command", required : false)
    input("offPattern", "text", title: "Off Pattern", description: "Name of pattern to use with \"Off\" command", required : false)
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
}

/**
 * helper methods for logging
 */
def logsOff() {
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

def logDebug(String str) {
  if (logEnable) { log.debug(str) }
}

/**
 * initialization 
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
    
// clear schedules and saved device data
    pbCloseSocket()
    unschedule()
    state.version = version()
    state.patternList = [ : ]
    state.lastSocketCall = now()
    
    device.updateDataValue("segments","0")
    
    sendEvent([name: "connectionStatus", value: WS_DISCONNECTED()])
    sendEvent([name: "activePattern", value: PATTERN_NOT_SET()])
    sendEvent([name: "level", value: defaultBrightness()])     
    
// connect to device     
    if (ip == null) {
      logDebug("initialize:  ip address not set.")
      return
    }
    
    runInMillis(1000,pbOpenSocket)
    runInMillis(1500,awaitConnection,[data: "initializePostConnection"])   
}

// initialization to be done after connection is
// established.
//
def initializePostConnection() {
    logDebug("initializePostConnection")

    getConfig()
    runInMillis(150,getVariables)      
    runInMillis(500,getPatterns) 
  
    runInMillis(1500,on)
   
    runEvery1Minute(DisconnectInactive)    
    runEvery30Minutes(getConfig)
    runEvery30Minutes(getVariables)
    runEvery1Hour(getPatterns) 
}

/**
 * networking infrastructure - connect, disconnect, send, status...
 */
def isConnected() {
  try {
    def status = device.currentValue("connectionStatus")
    def res = status.startsWith(WS_CONNECTED())
    logDebug("isConnected returning ${res}")
    
    return res
  }  
  catch(e) {
    // handles uninitialized connection status attribute
    return false
  }  
}

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
    runIn(1,awaitConnection,targetMethod)
    return
  }
  
  if (isConnected()) {
    if (targetMethod != null)
      this."${targetMethod}"()   
  }
}
 
// Scheduled at intervals to see whether we've gone
// several minutes with no user I/O.  If so, close 
// the connection to minimize net traffic.  
def DisconnectInactive() {
    t = (now() - state.lastSocketCall) / 1000
    if (isConnected() && (t >= idleWaitTime())) { 
        logDebug("Idle for ${t} seconds. Disconnecting.")
        pbCloseSocket()
    } 
}

def pbOpenSocket() {
    state.lastSocketCall = now()    
    if (isConnected()) return
    
    try {
      sendEvent([name: "connectionStatus", value: WS_WAITING()])     
      interfaces.webSocket.connect("ws://${ip}")
      pauseExecution(1000)
    }
    catch(e) {
      log.error("Exception in pbOpenSocket ",e) 
      sendEvent([name: "connectionStatus", value: WS_DISCONNECTED()])  
    }
}

def pbCloseSocket() {
    interfaces.webSocket.close()
}

def sendMsg(String s) {
    logDebug "sendMsg ${s}"
    pbOpenSocket()
    state.lastSocketCall = now()     
    interfaces.webSocket.sendMessage(s)
}

def webSocketStatus(String status) {
    logDebug("webSocketStatus: ${status}")
    
    if (status.startsWith("status: open")) {    
        sendEvent([name: "connectionStatus", value: WS_CONNECTED()]) 
    } 
    else if (status.startsWith("status: closing")) {
        sendEvent([name: "connectionStatus", value: WS_DISCONNECTED()])
    }    
    else {
        sendEvent([name: "connectionStatus", value: WS_DISCONNECTED()])
    }
}

// process hardware configuration packet from the pixelblaze
// current brightness, LED count, software version #, etc. 
// for now, we grab brightness so we can later set it to zero
// to implement a "hard" off switch which preserves current pattern
// state, then restores it when the user turns the pixelblaze 
// back "on".   
def parseHardwareConfig(Map json) {

  if (json.containsKey("brightness")) {
    logDebug("    found hardware config frame")  
 
    bri = Math.floor(json.brightness * 100)
    if ((bri > 0) && (bri != device.currentValue("level")))  {
      sendEvent([name: "level", value: bri])     
    } 
  }
}

// process json-ized list of exported variables from the pixelblaze.
// TBD - not yet implemented
def parseVariableList(Map json) {
  def i, status, segList

  if (json.containsKey("vars")) {
    logDebug("    found variable list.")

// does the list specify multiple segments?    
    if (json.vars.containsKey("__n_segments")) {
       device.updateDataValue("segments",json.vars.__n_segments.toString())
    }  
    else {
      return;
    }
          
// try to get a segment description for each child device          
    segList = getChildDevices()
    for (i = 0; i < segList.size(); i++) { 
        status = json.vars."z_${i}"
        logDebug("   segment z_${i} : ${status}")
         
        seg = segList[i]
        seg.updateDataValue("state",status[0].toString())
        seg.updateDataValue("hue",status[1].toString())        
        seg.updateDataValue("saturation", status[2].toString())
        seg.updateDataValue("brightness", status[3].toString())
        seg.updateDataValue("effect",status[4].toString())
        seg.updateDataValue("size", status[5].toString())
        seg.updateDataValue("speed",status[6].toString())
        
        seg.updateState(seg)
    }  
  }      
}     

// process json activeProgram packet, new with latest (v2.18 or so)
// pixelblaze firmware versions. 
// TBD - add support for per-program controls
def parseActiveProgram(Map json) {
  if (json.containsKey("activeProgram")) {
    logDebug("    found active program frame")
    
    id = json.activeProgram.activeProgramId   
    name = patternNameFromId(id) 
    logDebug("    active pattern is ${name}")
      
    sendEvent([name: "activePattern", value: name])        
  }
}

// handle json text frames
def parseJsonFrame(String frame) {
  logDebug("Received JSON frame:")
//logDebug(frame)  
     
  json = null
   
  try {
    json = new groovy.json.JsonSlurper().parseText(frame)
      if (json == null){
        logDebug "parseJsonFrame: JsonSlurper returned null"
        return
      }     
    parseHardwareConfig(json)
    parseActiveProgram(json)
    parseVariableList(json)
        
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
  logDebug("Received pattern list frame")

// convert the data portion of the hex string to a normal string 
  def byte[] listFrame = hubitat.helper.HexUtils.hexStringToByteArray(frame)
  def rawList = new String(listFrame,2,listFrame.length - 2)
      
// since program listings can be spread across multiple frames, we need to check
// for start and end frame tags, which are in the second decoded byte      
  if (listFrame[1] & 0x01) {  //look for 0x01 start frame indicator
    logDebug "Start Frame Detected"
    state.patternList = [ : ]              
  }

// convert list entries into a map, indexed by pattern name
  def newMap = rawList.tokenize("\n").collectEntries {
    it.tokenize("\t").with {[(it[1]):it[0]]}
  }
  state.patternList << newMap  
  
  if (listFrame[1] & 0x04) { // look for 0x04 completion frame indicator
    sz = state.patternList.size()
    logDebug "End Frame Detected. ${sz} patterns loaded."      
  }    
}

/**
 * parse data from Pixelblaze's websocket interface
 * NOTE - when you open the Pixelblaze's pattern editor in a browser,
 * it generates a fairly constant stream of packets, which need to 
 * be ignored as rapidly as possible so we don't bog down the Hubitat.
 * Don't be surprised if you're debugging while editing
 * patterns and you see a lot of packets whiz by.
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
 * Command handlers and associated helper functions
 */


// helper function - delete all children
private removeChildDevices(kids) {
	kids.each {deleteChildDevice(it.deviceNetworkId)}
} 
 
// remove current children and replace them according to the
// current number of segments reported by the pixelblaze.
// NOTE: Don't press this button until you're actually connected
// to your pixelblaze and have the proper pattern running.
def resetChildren() {
  def i,lstring,segs

  removeChildDevices(getChildDevices())
  logDebug("resetChildren - removing all child devices")

  if (isConnected()) {
    segs = device.getDataValue("segments").toInteger()  
    logDebug("  adding devices for ${segs} segments")
  
    for (i = 0; i < segs; i++) {
      lstring = device.getLabel() + " Segment " + i.toString()
      
      addChildDevice("Pixelblaze Segment", "${device.id}-${i}",
         	    		[label: lstring, 
                    	 isComponent: false, 
                         name: "Pixelblaze Segment ${i}"])    
    
    }      
    runInMillis(400,getVariables)
  }
}
 
def getConfig() {
    sendMsg("{ \"getConfig\" : true }")    
}

// Given the name of a pattern, return the
// pattern's ID string, or NULL if not found. 
def getPatternId(String name) {
    return state.patternList[name]
}    

// Tell the pixelblaze how many LEDs it should control
// (The effect can be... strange. Use with caution)
def setPixelCount(BigDecimal n) {
    sendMsg("{ \"pixelCount\" : ${n} }" )
}

// n should be in the range 0-100
def setGlobalBrightness(BigDecimal n) {
    if (n > 0) {n = n / 100.0} // avoid weirdness around floating point zero
    sendMsg("{ \"brightness\" : ${n} }" )
}
 
def setActivePattern(name) {
   def pid = getPatternId(name)
   if (pid != null) {
     def str = "{ \"activeProgramId\" : \"${pid}\" }"
     sendMsg(str)  
     sendEvent([name: "activePattern", value: name])     
   }
}

// Set exported pattern variables on the pixelblaze.
// Takes a json-formatted string of the names and values
// of variables exported in the currently active pattern.
// see readme.md and the Pixelblaze expression documentation
// for details.
def setVariables(String jsonString) {
    logDebug "setVariables ${jsonString}"
    def str = "{ \"setVars\" : ${jsonString}}"
    sendMsg(str)
}

// set brightness
// NOTE - duration is not currently supported
def setLevel(BigDecimal level,BigDecimal duration=0) {
    setGlobalBrightness(level) 
    sendEvent([name: "level", value: level]) 
    sendEvent([name: "switch", value: "on"])   
}

// request a json-ized list of exported variables from
// the currently active pattern.
// TBD - not yet exposed.
def getVariables() {
    sendMsg("{ \"getVars\" : true }")
}

// Update the list of pattern names and IDs
def getPatterns() {
    sendMsg("{ \"listPrograms\" : true }")    
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
    sendEvent([name: "switch", value: "on"])       
}

def off() {   
    stopSequencer()
    
    if (switchWithPatterns) {
      setActivePattern(offPattern)
    }
    else {
      setGlobalBrightness(0)
    }
    sendEvent([name: "switch", value: "off"])      
}

def startSequencer() {
  if (device.currentValue("activePattern") != PATTERN_SEQUENCER_RUNNING()) { 
    on()  
    sendMsg("{\"sequencerEnable\": true, \"runSequencer\" : true }") 
    sendEvent([name: "activePattern", value: PATTERN_SEQUENCER_RUNNING()])         
  }  
}

def stopSequencer() {
  if (device.currentValue("activePattern") == PATTERN_SEQUENCER_RUNNING()) {   
    sendMsg("{\"sequencerEnable\": false, \"runSequencer\" : false }")   
    sendEvent([name: "activePattern", value: PATTERN_NOT_SET()])      
  }
}

def setSequenceTimer(BigDecimal n) {
    sendMsg("{ \"sequenceTimer\" : ${n} }" )
}

def randomPattern() {
   rnd = new Random() 
   try {
     name = (state.patternList.keySet() as List)[rnd.nextInt(state.patternList.size())]   
     setActivePattern(name)
   } 
   catch(e) {
     logDebug("randomPattern called on empty pattern list")
   }   
}

def indexFromPatternName(pattern) {
  if (pattern == PATTERN_NOT_SET())  return 0  // special case, no pattern list
  
  def name = (state.patternList?.keySet() as List)  
  if (name == null) return -1 
  return name.indexOf(pattern)   
}

def patternNameFromId(id) {
  name = null
 
  try {   
    if ((state.patternList != null) && (id != null)) {
      name = state.patternList.find { it.value == id }.key
    }  
  }
  catch (e) {
    logDebug("patternNameFromId - id not found")
  }
    
  return (name != null) ? name : PATTERN_NOT_SET() 
}

def patternNameFromIndex(n) {
  def name = (state.patternList?.keySet() as List)  
  return name[n]
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

