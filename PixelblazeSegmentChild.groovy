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
 *    Date        Ver      What
 *    ----        ---      ----
 *    2020-03-30   0.1a     Created
 *    2020-07-22   1.1.1    Status update improvements
 *    2020-12-05   1.1.3    Hubitat Package Manager Support/resync version w/main driver
 *    2021-02-02   2.0.0    Segment data now stored in main hub Pixelblaze driver
 *    2021-01-30   2.0.4    Sync version w/main driver - no change
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

@Field static Map lightEffects = [
    0:"none",
    1:"Glitter",
    2:"Rainbow Bounce",
    3:"KITT",
    4:"Breathe",
    5:"Slow Color",
    6:"Snow",
    7:"Chaser Up",
    8:"Chaser Down",
    9:"Strobe",
   10:"Random Wipe Up",
   11:"Random Wipe Down", 
   12:"Springy Theater",
   13:"Color Twinkles",
   14:"Plasma",
   15:"Ripples",
   16:"Spin Cycle",
   17:"Rainbow Up",
   18:"Rainbow Down",
]
 
def version() {"2.0.3"}
def segmentDescriptorSize() { 7 }

metadata {
    definition (name: "Pixelblaze Segment",
                namespace: "ZRanger1",
                author: "ZRanger1(JEM)",
                importUrl: "https://raw.githubusercontent.com/zranger1/hubitatpixelblazedriver/master/PixelblazeSegmentChild.groovy") {
        capability "Actuator"
        capability "Initialize"
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
// returns the Pixelblaze segment index for the segment
// associated with this child
def Integer getSegmentId() {   
    return (device.deviceNetworkId.split('-')[1]).toInteger()
}

// get the current state of this segment from the master driver
def getSegmentData() {
  segNo = getSegmentId()
  return getParent().getSegmentData(segNo)
}

// save segment descriptor map to master driver's database
def saveSegmentData(Map segmentData) {
  segNo = getSegmentId()
  getParent().setSegmentData(segNo,segmentData)
}

// retrieve the segment's state information from the master driver's database,
// and set both the segment driver and the pixelblaze to reflect it.
def updateDriverState() {
    def tmpStr, tmpVal
    segData = getSegmentData()
     
// on/off state
  tmpStr = (segData["state"]) ? "on" : "off"
  sendEvent([name: "switch", value: tmpStr])  

// color & brightness
  tmpVal = Math.floor(100.0 * segData["hue"])
  sendEvent([name: "hue", value: tmpVal])     
  
  tmpVal = Math.floor(100.0 * segData["sat"])
  sendEvent([name: "saturation", value: tmpVal])     
  
  tmpVal = Math.floor(100.0 * segData["bri"])
  sendEvent([name: "level", value: tmpVal])     
  
// effect
  tmpVal = Math.floor(segData["effect"])
  sendEvent([name:"effectNumber", value: tmpVal])    
  
// segment size
  tmpVal = Math.floor(segData["size"])
  sendEvent([name:"segmentSize", value: tmpVal])   

// effect speed 
  tmpVal = Math.floor(200 * segData["speed"])
  sendEvent([name:"effectSpeed", value: tmpVal]) 
    
  saveSegmentData(segData)  
}

/**
 * initialization & configuration
 */ 
def installed(){
    log.info "Pixelblaze Segment Driver ${version()} id:${device.deviceNetworkId} installed."  
    
    def name = getSegmentId(); 
    log.debug("Installed segment index ${name}")
    
// fetch current settings from master database    
    initialize()
    updateDriverState()
}

def updated() {
    log.info "Pixelblaze Segment Driver ${version()} id:${device.deviceNetworkId} updated."
    initialize()
}

def initialize() {
  state.version = version()
  
//  def eff = new groovy.json.JsonBuilder(lightEffects)    
  sendEvent(name:"lightEffects",value: lightEffects)    
}

// handle command responses & status updates from bulb 
def parse(String description) {
  logDebug("parse: ${description}") 
}

/**
 * Command handlers and associated helper functions
 */
 
// Switch commands 
def on() {   
    m = getSegmentData()
    m["state"] = 1
    saveSegmentData(m)
    
    sendEvent([name: "switch", value: "on"])     
}

def off() {
    m = getSegmentData()
    m["state"] = 0
    saveSegmentData(m)
    
    sendEvent([name: "switch", value: "off"]) 
}

// ColorControl commands
def setColor(hsv) {
    logDebug("setColor(${hsv})") 
    
    m = getSegmentData()
    
    def n = hsv.hue / 100.0
    m["hue"] = n    

    n = hsv.saturation / 100.0
    m["sat"] = n    
    
    n = hsv.level / 100.0
    m["bri"] = n
    
    saveSegmentData(m)    
        
    sendEvent([name: "hue", value: hsv.saturation])      
    sendEvent([name: "saturation", value: hsv.saturation])           
    sendEvent([name: "level", value: hsv.level]) 
    setGenericColorName(m)    
}

def setHue(h) {
    def n = h / 100.0
    
    m = getSegmentData()
    m["hue"] = n
    saveSegmentData(m)
    
    sendEvent([name: "hue", value: h]) 
    setGenericColorName(m)
}

def setSaturation(sat) {
    def n = sat / 100.0
    m = getSegmentData()
    m["sat"] = n
    saveSegmentData(m)
    
    sendEvent([name: "saturation", value: sat]) 
    setGenericColorName(m)    
}

// SwitchLevel commands
// set brightness
def setLevel(BigDecimal lev,BigDecimal duration=0) { 
    def b = lev / 255.0; // scale to 0-1 for Pixelblaze
    
    m = getSegmentData()
    m["bri"] = b
    saveSegmentData(m)    
     
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
  
   m = getSegmentData()
   m["size"] = s
   saveSegmentData(m)
  
   sendEvent([name: "segmentSize", value: s])    
}

// LightEffects commands
def setEffect(BigDecimal effectNo) {

// clamp to integer range of available effects
  def val = effectNo.toInteger()
  val = (val > 0) ? Math.min(val,lightEffects.size() - 1) : 0

  logDebug("setEffect to ${effectNo}.")
    
  def name = lightEffects[val]
  
  m = getSegmentData()
  m["effect"] = val
  saveSegmentData(m)  

  sendEvent([name:"effectNumber", value:val])
  sendEvent([name:"effectName", value:name])      
}

def setNextEffect() {
  m = getSegmentData()
  def i = m["effect"]
  if (++i >= lightEffects.size()) i = 1
  
  setEffect(i) 
}
      
def setPreviousEffect() { 
  m = getSegmentData()
  def i = m["effect"]
  if (--i < 0) i = ((lightEffects.size() - 1)) 
  
  setEffect(i)  
}

def setEffectSpeed(BigDecimal speed) {
  // convert speed to 0..1 range for PB, so that the UI
  // setting 100 == 0.5, which gives us a comfortable 
  // default and room in both directions.    
  speed = (speed > 0) ? Math.min(speed.toFloat(),200.0) : 0
  def deviceSpeed = speed / 200
   
  m = getSegmentData()
  m["speed"] = deviceSpeed
  saveSegmentData(m)  
    
  logDebug("setEffectSpeed to ${speed}")    
  sendEvent([name: "effectSpeed", value: speed])     
}

// A very rough approximation, based on empirical observation
// of some random WS2812 LEDs.  The exact color is 
// a little erratic at the lowest brightness levels.
def setGenericColorName(Map segData){
    def colorName = "(not set)"
    
    if (segData["sat"] < 0.17) {
      colorName = "White"
    }
    else {
      hue = Math.floor(segData["hue"] * 100)
      switch (hue.toInteger()){
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

