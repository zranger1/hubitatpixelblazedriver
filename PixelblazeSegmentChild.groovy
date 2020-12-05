/**
 *
 *  File: PixelblazeSegmentChild.groovy
 *  Platform: Hubitat
 * 
 *  Allows you subdivide your LED strip into two or more contiguous subranges
 *  of pixels, treating each "zone" as a separate color bulb device on the Hubitat
 *  
 *  Requirements:
 *    A Pixelblaze controller w/addressable LED strip, and a pattern
 *    running on the controller that supports the "zone" protocol.
 *
 *    Date        Ver           Who       What
 *    ----        ---           ---       ----
 *    2020-03-30   0.1a          JEM       Created
 *    2020-07-22   1.1.1         JEM       Status update improvements
 *    2020-12-05   1.1.3         JEM       Hubitat Package Manager Support/resync version w/main driver
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
import groovy.transform.Field
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

/**
 * Constants and configuration data
 */
import groovy.transform.Field

@Field static List lightEffects = [
    "none",
    "Glitter",
    "Rainbow Bounce",
    "KITT",
    "Breathe",
    "Slow Color",
    "Snow",
    "Chaser Up",
    "Chaser Down",
    "Strobe",
    "Random Wipe",
    "Springy Theater",
]
 
def version() {"1.1.3"}

metadata {
    definition (name: "Pixelblaze Segment",
                namespace: "ZRanger1",
                author: "ZRanger1(JEM)",
                importUrl: "https://raw.githubusercontent.com/zranger1/hubitatpixelblazedriver/master/PixelblazeSegmentChild.groovy") {
        capability "Actuator"
        capability "LightEffects"
        capability "Switch"
        capability "SwitchLevel"
        capability "ColorControl"  

        command    "setSize",["number"]
        command    "setEffectSpeed", [[name: "Effect speed*", type: "NUMBER", description: "(0 to 200)", constraints:[]]]                  
        attribute  "effectNumber","number"
        attribute  "effectSpeed", "number" 
        attribute  "segmentSize", "number"
    }
}

preferences {
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false      
}

/**
 * helper methods for logging
 */
def logDebug(String str) {
  if (logEnable) { log.debug(str) }
}


/**
 * helpers for building json command strings
 */
def getSegmentIndex() {
  def str = device.deviceNetworkId.split('-')
  return str[1].toInteger() 
}

// retrieve the current control state of the segment index in
// the floating point array form Pixelblaze wants.
def getSegmentStateArray() {
  def keys = ["state","hue","saturation","brightness","effect","size","speed"]
  def result = new BigDecimal[keys.size()]
  
  for (def i = 0; i < keys.size(); i++) {
    result[i] = new BigDecimal(device.getDataValue(keys[i]))
  }
  
  return result  
}

// Send the currently stored segment data to the pixelblaze
def doPixelblazeCommand() {
  def i = getSegmentIndex()
  def m = getSegmentStateArray()    
  def cmd = JsonOutput.toJson("z_${i}":m) + "}" 
  
  getParent().setVariables(cmd) 
}

/**
 * initialization & configuration
 */ 
def installed(){
    log.info "Pixelblaze Segment driver ${version()} id:${device.deviceNetworkId} installed."  
    
    def index = getSegmentIndex(); 
    log.debug("Installed segment index ${index}")    
    
    // initialize device local data
    device.updateDataValue("state","1")
    device.updateDataValue("hue","0")        
    device.updateDataValue("saturation","1")
    device.updateDataValue("brightness", "0.8")
    device.updateDataValue("effect","0")
    device.updateDataValue("size", "0") 
    device.updateDataValue("speed","1")    
}

def updated() {
    log.info "Pixelblaze segment driver ${version()} id:${device.deviceNetworkId} updated."
    initialize()
}

def initialize() {
  state.version = version()
  
  def eff = new groovy.json.JsonBuilder(lightEffects)    
  sendEvent(name:"lightEffects",value: eff)    
}

// handle command responses & status updates from bulb 
def parse(String description) {
  logDebug("parse: ${description}") 
}

/**
 * Command handlers and associated helper functions
 */
 
// called by parent when it gets an update from the pixelblaze 
def updateState(dev) {
  def tmpStr, tmpVal
  
// on/off state
  tmpStr = (dev.getDataValue("state") == "1") ? "on" : "off"
  sendEvent([name: "switch", value: tmpStr])  

// color & brightness
  tmpVal = Math.floor(100.0 * dev.getDataValue("hue").toFloat())
  sendEvent([name: "hue", value: tmpVal])     
  
  tmpVal = Math.floor(100.0 * dev.getDataValue("saturation").toFloat())
  sendEvent([name: "saturation", value: tmpVal])     
  
  tmpVal = Math.floor(100.0 * dev.getDataValue("brightness").toFloat())
  sendEvent([name: "level", value: tmpVal])     
  
// effect
  tmpVal = dev.getDataValue("effect")
  sendEvent([name:"effectNumber", value: tmpVal])    
  
// segment size
  tmpVal = dev.getDataValue("size")
  sendEvent([name:"segmentSize", value: tmpVal])   

// effect speed
  tmpVal = Math.floor( 100 * dev.getDataValue("speed").toFloat())
  sendEvent([name:"effectSpeed", value: tmpVal])   
 
}
 
// Switch commands 
def on() {   
    device.updateDataValue("state","1")
    doPixelblazeCommand()
    
   sendEvent([name: "switch", value: "on"])     
}

def off() {
    device.updateDataValue("state","0")
    doPixelblazeCommand()
    
    sendEvent([name: "switch", value: "off"]) 
}

// ColorControl commands
// sends all the events necessary to keep the light's state

def setColor(hsv) {
    logDebug("setColor(${hsv})") 
    
    def n = hsv.hue / 100.0
    device.updateDataValue("hue",n.toString())  

    n = hsv.saturation / 100.0
    device.updateDataValue("saturation",n.toString())    
    
    n = hsv.level / 100.0
    device.updateDataValue("brightness",n.toString())        
    
    doPixelblazeCommand()
    
    sendEvent([name: "hue", value: hsv.saturation])      
    sendEvent([name: "saturation", value: hsv.saturation])           
    sendEvent([name: "level", value: lev])         
}

def setHue(h) {
    def n = h / 100.0
    
    device.updateDataValue("hue",n.toString())    
    doPixelblazeCommand()
    
    sendEvent([name: "hue", value: h])  
}

def setSaturation(sat) {
    def n = sat / 100.0
    device.updateDataValue("saturation",n.toString())    
    doPixelblazeCommand()
    
    sendEvent([name: "saturation", value: sat])       
}

// SwitchLevel commands
// set brightness
def setLevel(BigDecimal lev,BigDecimal duration=0) { 
    def b = lev / 255.0; // scale to 0-1 for Pixelblaze
    
    device.updateDataValue("brightness",b.toString());   
    doPixelblazeCommand()   
    
    sendEvent([name: "level", value: lev]) 
}

// set segment size -- NOTE: This performs minimal
// error checking.  Use with care.
// With great power, comes... well, you know.
def setSize(BigDecimal s) {
  
// TBD - check for reasonable values. For now,
// we just kick out anything completely egregious
  if (( s < 1) || (s > 1000)) {
    logDebug("setSize(${s}) : Invalid Parameter")
    return
  }
  
  device.updateDataValue("size",s.toString()) 
  doPixelblazeCommand()
}

// LightEffects commands
def setEffect(BigDecimal effectNo) {

// clamp to integer range of available effects
  def val = effectNo.toInteger()
  val = (val > 0) ? Math.min(val,lightEffects.size() - 1) : 0

  logDebug("setEffect to ${effectNo}.")
    
  def name = lightEffects[val]
  
  device.updateDataValue("effect",val.toString())
  doPixelblazeCommand()
  sendEvent([name:"effectNumber", value:val])
  sendEvent([name:"effectName", value:name])      
}

def setNextEffect() {
  def i = device.getDataValue("effect").toFloat() 
  if (++i >= lightEffects.size()) i = 1
  
  setEffect(i) 
}
      
def setPreviousEffect() { 
  def i = device.getDataValue("effect").toFloat() 
  if (--i < 0) i = ((lightEffects.size() - 1)) 
  
  setEffect(i)  
}

def setEffectSpeed(BigDecimal speed) {
  speed = (speed > 0) ? Math.min(speed.toFloat(),200.0) : 0
  logDebug("setEffectSpeed to ${speed}")
  
  def deviceSpeed = Math.max(0.005,(200 - speed) / 100.0)
  device.updateDataValue("speed",deviceSpeed.toString())
  doPixelblazeCommand()  
  sendEvent([name: "effectSpeed", value: speed])     
}



