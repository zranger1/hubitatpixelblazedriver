# hubitatpixelblazedriver v1.00
Hubitat Elevation device handler for the Pixelblaze addressable LED controller.

## What is a Pixelblaze?
For information on the Pixelblaze, visit its home:  www.electromage.com

Pixelblaze is an ESP32 based programmable LED controller with a fast
fixed-point integer Javascript-like interpreter built in. It's very easy
to create and save complex lighting patterns, animated or not.

I stumbled on Pixelblaze while looking for a way to light some very long beams
in multiple colors at a reasonable price, without involving multiple Hue strip
controllers. Pixel addressable LED strips are perfect for this job. Not
only do I have very nice color coordinated lighting, I will never have to buy
holiday lights again, AND I can have endless fun running cellular automata on my
ceiling!

## What can I do with this driver?
This driver gives the Hubitat basic control over the Pixelblaze - on/off, brightness,
get pattern list, set active pattern, etc.. It works with RuleMachine, so you can 
coordinate the Pixelblaze with your other home automation devices.

For example I have mine set up to automatically turn on/off depending on ambient light level,
in sync with the rest of my Hue setup. I also have it run a nice candlelight pattern for a few
minutes if a motion detector sees somebody coming downstairs in the middle of the night. 

## What's New in this Version (1.00)
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

## To Use
Set up your Pixelblaze.  Take note of it's IP address.

On your Hubitat Elevation's admin page, select "Drivers Code", then click the
"New Driver" button.  Paste the Groovy driver code from this repository into 
the editor window and click the "SAVE" button.

Create a new virtual device on your Hubitat, name and label it, and select 
"Pixelblaze Controller" as the device type.  Save your new device.

Click the new device on the Hubitat's "Devices" page, and enter your Pixelblaze's
IP address and port number (which should be *81*) in the provided field.

If you are using on and off patterns (selected by switch in device preferences),
you will also need to enter the names of the patterns to use for the "On" and "Off"
states. You can use any pattern stored on your Pixelblaze.

For the "Off" pattern, I just wrote a quick pattern that does
nothing when called to render pixels.  Like this:
```javascript
   export function render(index) {
     ; // do absolutely nothing
   }
```
But again, you can have "On" and "Off" do anything you like!


