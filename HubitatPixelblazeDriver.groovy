/**
 *
 *  File: HubitatPixelblazeDriver
 *  Platform: Hubitat
 *
 *  Allows hubitat to control a Pixelblaze addressable LED controller. 
 *  Information on the Pixelblaze is available at www.electromage.com.
 *  Note that I have nothing at all to do with the hardware.  I just
 *  like the device!
 *
 *  Requirements:
 *    A Pixelblaze controller on the local LAN. 
 *
 *    Date        Ver			Who       What
 *    ----        ---     ---       ----
 *    2019-7-31   0.1			JEM       Created
 *
 */

def version() {"v0.1"}

import hubitat.helper.InterfaceUtils
import hubitat.helper.HexUtils

metadata {
    definition (name: "Pixelblaze Controller", namespace: "jem", author: "JEM", importUrl: "") {
        capability "Switch"
		capability "Initialize"

        command "setActivePattern", ["string"]
        command "setVariable", ["string","string"]
		command "setBrightness",["number"]
        command "disconnect"
        command "reconnect"
		command "getVariables"
		command "getPatterns"
    }
}

preferences {
    input("ip", "text", title: "IP Address", description: "IP address of Pixelblaze, including port #", required: true)
	input("onPattern", "text", title: "On Pattern", description: "Name of pattern to use with \"On\" command", required : true)
	input("offPattern", "text", title: "Off Pattern", description: "Name of pattern to use with \"Off\" command", required : true)
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
}

/**
 * Required methods
 */
def initialize() {
    log.debug "initialize"
	
// reset all state variables, clear pattern map	
    state.version = version()
	state.patternList = [ : ]
	
	if (state.isSocketOpen) {
	  pbCloseSocket()
	  state.isSocketOpen = false
	} 
	
// open a websocket to the pixelblaze, give it a 
// second to stabilize, and get the list of available 
// programs
    pbOpenSocket()
    runIn(3, getPatterns)
}

def installed() {
    log.debug "installed"
    initialize()
}

def updated() {
    log.debug "updated"
    unschedule()  
    if (logEnable) runIn(1800,logsOff)
	initialize()
}

def logsOff() {
    log.debug "logsOff"
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

/**
 * parse data returned from Pixelblaze's websocket interface
 */ 
def parse(String frame) {
  log.debug "received frame from pixelblaze"

// determine what kind of frame it is. Variable lists come in as JSON
// strings, program lists are hexStrings, with a signature 0x07 as the 
// first encoded byte.  
  if (frame.startsWith("07")) {
      log.debug "Found program list binary frame"

// convert the data portion of the hex string to a normal string 
      def byte[] listFrame = hubitat.helper.HexUtils.hexStringToByteArray(frame)
      def rawList = new String(listFrame,2,listFrame.length - 2)
      
// since program listings can be spread across multiple frames, we need to check
// for start and end frame tags, which are in the second decoded byte      
	  if (listFrame[1] & 0x01) {  //look for 0x01 start frame indicator
	    log.debug "Start Frame Detected"
  	    state.patternList = [ : ]	          
	  }
	  if (listFrame[1] & 0x04) { // look for 0x04 completion frame indicator
	    log.debug "End Frame Detected"   
	 } 
	 // convert list entries into a map, indexed by pattern name
     def newMap = rawList.tokenize("\n").collectEntries {
       it.tokenize("\t").with {[(it[1]):it[0]]}
     }
	 state.patternList << newMap  
  }  
  else {
   // it is a json (string) frame containing variables or other info
   // TBD - initial version does nothing although it exposes
   // the getVariables command. Don't have a compelling driver
   // level use case atm. Seems like more an application thing,
   // unless I want the driver to start having a priori knowledge
   // of certain patterns wired in.  Need to think about it.
   log.debug "JSON frame detected"
   json = null
   try {
      json = new groovy.json.JsonSlurper().parseText(frame)
        if (json == null){
          log.debug "JsonSlurper failed (null result)"
		  log.debug frame
          return
        } 
    }
	catch(e) {
       log.error("Exception while parsing json frame: ",e)
	   log.error frame
       return
    } 
    log.debug json	// TBD - do something fun with this data!
  }
}

/**
 * connnect to the pixelblaze's websocket 
 * TBD - build a scheduled connection checker. Right now the socket is
 * opened only by initialize() or by user command. It's been reliable so far,
 * but out in the real world, we need to be a bit more proactive about such things. 
 */
def pbOpenSocket() {
    log.debug "pbOpenSocket"
	
	if (!state.isSocketOpen) {
        try {
            interfaces.webSocket.connect("ws://${ip}")
        }
        catch(e) {
          log.error("Exception on websocket open: ",e)
        }
    }
}

def pbCloseSocket() {
    log.debug "pbCloseSocket"
	interfaces.webSocket.close()
}

def sendMsg(String s) {
    log.debug "sendMsg"
    if (state.isSocketOpen) {
      interfaces.webSocket.sendMessage(s)
	}
}

/**
 * Handler for message from the system websocket interface.  
 */
def webSocketStatus(String status){
    log.debug "webSocketStatus: ${status}"
	
	if (status == "status: open") {
        pauseExecution(1000)
        state.reconnectDelay = 1
		state.isSocketOpen = true
    } 
    else if (status == "status: closing") {
	    state.isSocketOpen = false
    }	
	else {
	    log.error("webSocketStatus: unexpected status")
		state.isSocketOpen = false
	}
}

/**
 * Given the name of a pattern, return the
 * pattern's ID string, or NULL if not found.
 */ 
def getPatternId(String name) {
    log.debug "getPatternId"
	return state.patternList[name]
}	

/**
 * Command handlers
 */
def setActivePattern(name) {
   log.debug "setActivePattern(${name})"
   
   def pid = getPatternId(name)
   def str = "{ \"activeProgramId\" : \"${pid}\" }"
   sendMsg(str)  
}

def setVariable(name,value) {
// TBD - setVariable needs some intelligence around 
// conversion of hubitat data types to the 0..1 fixed point
// integers used for most pixelblaze numeric variables. 
// The ruleset will be something like: (1) if something can
// be converted to a number, it will be, (2) strings that begin
// with '#' are assumed to represent hexadecimal bytes and will
// be converted accordingly, (3) everything else will be passed
// on untouched, as a string.
    log.debug "setVariable ${name},${value}"
    def str = "{ \"setVars\" : { \"${name}\" : ${value} } }"
    sendMsg(str)
}

def setBrightness(level) {
// this is semi-undocumented in the current pixelblaze software
// it may change without warning and stop working at some point.	
    log.debug "setBrightness"
	sendMsg("{ \"brightness\" : ${level} }" )
}

def getVariables() {
    log.debug "getVariables"
	sendMsg("{ \"getVars\" : true }")
}

def getPatterns() {
    log.debug "getPatterns"
	sendMsg("{ \"listPrograms\" : true }")	
}

def on() {
    log.debug "on"
	setActivePattern(onPattern)
}

def off() {
    log.debug "off"
	setActivePattern(offPattern)
}

def disconnect() {
    log.debug "disconnect"
	pbCloseSocket()	
}

def reconnect() {
    log.debug "reconnect"
	initialize()	
}
