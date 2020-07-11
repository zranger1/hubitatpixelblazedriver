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
 *    2020-3-30   0.1a          JEM       Created
 *    2020-7-11   0.2a          JEM       Status update improvements

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
    "Effect1",
    "Effect2",
    "Effect3",
    "Effect4",
    "Effect5",
    "Effect6",
    "Effect7",
    "Effect8",
    "Effect9",
    "Effect10",
    "Effect11",
    "Effect12",
    "Effect13",
    "Effect14",
    "Effect15",
]
 
def version() {"v.02a"}

metadata {
    definition (name: "Pixelblaze Segment", namespace: "jem", author: "JEM",importUrl: "") {
        capability "Actuator"
        capability "LightEffects"
        capability "Switch"
        capability "SwitchLevel"
        capability "ColorControl"  
        
        command     "setSize",["number"]
//        command     "updateState"
        
        attribute  "effectNumber","number"
        attribute  "segmentSize", "number"
    }
}

preferences {
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true      
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
  def keys = ["state","hue","saturation","brightness","effect","size"]
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
// error checking in this version.  Use wisely.
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
  val = (val > 0) ? ((val < lightEffects.size()) ? val : lightEffects.size()) : 0;

  logDebug("setEffect to ${effectNo}.")
    
  def name = lightEffects[val]
  
  device.updateDataValue("effect",val.toString());
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

// A very rough approximation, based on empirical observation
def setGenericColorName(hsv){
    def colorName = "(not set)"
    
    if (hsv.saturation < 17) {
      colorName = "White"
    }
    else {
      switch (hsv.hue.toInteger()){
          case 0..2: colorName = "Red"
              break
          case 3..6: colorName = "Orange"
              break
          case 7..10: colorName = "Yellow"
              break
          case 11..13: colorName = "Chartreuse"
              break
          case 14..34: colorName = "Green"
              break
          case 35..68: colorName = "Blue"
              break
          case 69..73: colorName = "Violet"
              break
          case 74..83: colorName = "Magenta"
              break
          case 84..98: colorName = "Pink"
              break
          case 99..100: colorName = "Red"
              break
        }
    }
    
    sendEvent(name: "colorName", value: colorName)
}



