# Change Log History For HubitatPixelblazeDriver.groovy 
See README.md for information on the latest version

### What's New -- Version 2.0.2
- **Expanded compatibility with Rule Machine and other automation tools** - Many more custom actions and attributes are now visible to RM.
- **Read variables exported from Pixelblaze patterns** - use getVariable(var name) to read
the variable from the Pixelblaze, then use the getVariableResult attribute to include the 
variable in your HE automations.
- **Support for color and speed controls in patterns.** - if a pattern includes hsv or rgb 
color controls, the driver will detect them and will allow you adjust the pattern using
the Hub's normal color bulb controls.  Similarly if the pattern includes a "Speed" slider,
the driver will allow you to control the pattern's speed via the "SetEffectSpeed" command.  Note
that not all patterns have color/speed controls.  

- **Greatly Improved Multisegment support** -- You no longer need to modify the pattern code to
make your segment settings persistent.  Multisegment settings are now saved on the hub, and
are loaded automatically when you load the new multisegmentforautomation pattern on your Pixelblaze. You can 
adjust the number and size of segments (up to 12) from the driver. See the [multisegment setup guide](https://github.com/zranger1/PixelblazePatterns/blob/master/MULTISEG_HA.md)
for details. 

- **Updated sequencer functionality** - supports the new "shuffle" and "playlist" sequencer modes
available in the latest Pixelblaze firmware.

- **Many performance & reliability improvements** 

### Version 1.1.1
You can now subdivide an LED strip into multiple segments and control each one
individually via a child device on the hub.   The segmentation is done on the 
pixelblaze, using a the pattern Multisegment.epe, which available from the Pixelblaze
pattern library. (You can also get the very latest .js version from my pattern
repository - https://github.com/zranger1/PixelblazePatterns )

Because segmentation is controlled by a pattern run on the Pixelblaze, the Hubitat
device handler does not automatically set up child devices.  To do so, you will need to
download and run the Multisegment pattern on your pixelblaze. Then install the both the parent and child
drivers into "Drivers Code" on your hubitat and create your devices.

Once you've created a device on the hub and connected it to your pixelblaze, you can create the child devices segments
by pressing the "Get Variables" button, followed by the "Reset Children" button.  ("Reset Children alone should do the job,
but especially on initial setup, it helps reliability to "Get Variables" first just to make sure a connection
is established.

Each child device controls a segment of the LED strip as though it were an RGB bulb.  The number of 
segments is controlled by code on the pixelblaze side, and can be changed if you (1) know a little
javascript, and (2) follow the naming conventions in the code (e.g. The segment numbering scheme
starts from zero. The control array for the xth segment must be named "z_x". If you do change the number of 
segments on the Pixelblaze side, you will need to "Reset Children" before the Hub driver will recognize
your new setup. 

Per segment special effects are supported.  The twelve effects initially included are:

0. **Default** - render the currently selected solid color
1. **Glitter** - fast random sparkles in the current color
2. **Rainbow Bounce** - the default "New Pattern" effect.
3. **KITT** - because KITT is essential. 
4. **Breathe** - brightness slowly "breathes" up and down
5. **Slow Color** - slowly changes hue
6. **Snow** - Occasional icy "sparkles" over current color background.
7. **Chaser Up** - light moves "up" from start of strip.
8. **Chaser Down** - light moves "down" from end of strip.
9. **Strobe** - hideous, but you never known when a rave may occur. Be prepared.
10. **Random Wipe** - random color wipe
11. **Springy Theater** - theater style chaser lights that also change distance.

**New:** If you're using multisegment with a home automation system, the Pixelblaze Web UI sliders may interfere with
your setup.  So as of multisegment v1.0.2, there is a variable in the pattern that allows you to completely
disable the sliders once you've got things set up.  It's ```useSliderUI``` around line
80.  Set it to 0 to disable sliders, to 1 to enable them.

For more information, see the README.md for patterns in https://github.com/zranger1/PixelblazePatterns


### Version 1.0.0
#### Alternate on/off method
You can now choose between two methods:

1.Set specific patterns to use for on and off states. The advantage of this method
is that it gives you complete control over what "on" and "off" actually mean.

2.Use hardware brightness to switch on and off.  This has the advantage of
being completely pattern independent.  It works like... an on/off switch.

To preserve compatibility with the previous version, the default is method 1 - using
patterns. To switch to the new method, just unset the "Use Patterns for On and Off"
switch in preferences, then save preferences. 

#### Lazy Connection
Connects to the Pixelblaze via websocket only when necessary to keep traffic and load on
devices to a minimum.  You are connected only when you issue a command or request status.
After a short period of inactivity (configurable via a constant in the code), the connection
is closed.

#### Auto-reconnect and Sync
The device handler now periodically polls for status and configuration. So if you use the
Pixelblaze's web interface to change settings or edit patterns, or if the Pixelblaze
(or the Hubitat) should go offline, connection will be automatically reestablished, and
hardware state and pattern list resynced.

It's not instant - if you're actively working on things and need connection *now*, use the
"Initialize" and "Get Patterns" buttons, but if left unsupervised, the device handler will
quickly return to its normal working state. 

#### Improved Pattern Handling
Quality-of-life improvements.  "Set Active Pattern" is joined by "Random Pattern", "Next" 
and "Previous" to make switching and exploring patterns a litte easier.  I've also exposed
the Pixelblaze's internal pattern sequencer, which rotates between
all available patterns on a timer.  

#### SetVariables (replaces SetVariable)
The SetVariables command takes a json-formatted string composed of the names and values
of variables exported by the currently active pattern.
 
Here is a Pixelblaze pattern, which I have named "Alert", to let you experiment with
the feature:

```javascript
export var hue = 0
export var speed = 0.03

export function beforeRender(delta) {
  t1 = triangle(time(speed))
}

export function render(index) {
  f = index/pixelCount
  edge = clamp(triangle(f) + t1 * 4 - 2, 0, 1)
  v = triangle(edge)
  hsv(hue, 1, v)
}
```
To use it, paste the script into the Pixelblaze's pattern editor, name it, and save it. 
The pattern exports two variables, "speed" and "hue", which control exactly what
you'd think.
 
By default, "Alert" cycles about once per second (speed == 0.03) and is bright
red (hue == 0)  To slow it down and turn it green, send the following json string:
 
```javascript 
    {"speed": 0.08, "hue": 0.33}
```         
The Pixelblaze supports a limited number of types -- booleans, numbers, and arrays. Color
parameters are all scaled to the range 0 to 1, thus 0.33 == 120 degrees == green in the
HSB color space. See the Pixelblaze expression and advanced documentation for details.

NOTE: This is totally fire-and-forget.  It does not check to see if the variables
exist nor does it validate the input values.  
