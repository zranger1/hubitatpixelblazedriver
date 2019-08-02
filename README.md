# hubitatpixelblazedriver
Hubitat elevation device handler for the Pixelblaze addressable LED controller.

# What is a Pixelblaze?
--------------------------
For information on the Pixelblaze, visit its home:  www.electromage.com

Pixelblaze is an Arduino based programmable LED controller with a fast
fixed-point integer Javascript-like interpreter built in. It's very easy
to create and save complex lighting patterns, animated or not.

I stumbled on Pixelblaze while looking for a way to light some very long beams
in multiple colors at a reasonable price, without involving multiple Hue strip
controllers. Pixel addressable LED strips are perfect for this job. Not
only do I have very nice color coordinated lighting, I will never have to buy
holiday lights again, AND I can have endless fun running cellular automata on my
ceiling beams!

# What can I do with this driver?
-----------------------------------
This driver gives the Hubitat basic control over the PixelBlaze - on/off, read pattern
list, set active pattern, etc..  It works with RuleMachine, so you can use whatever 
else you've got running on your Hubitat to manage the Pixelblaze.

For example I have mine set up to automatically turn on/off depending on ambient light level,
in sync with the rest of my Hue setup.  I also have it run a nice candlelight pattern for a few
minutes if a motion detector sees somebody coming downstairs in the middle of the night.

In future versions, I plan to add child devices that can interact with particular patterns, i.e.
breaking the strips into multiple zones and having each zone track the color of the nearest
Hub bulb.  Or silly things like picking up sunset colors from a webcam in , or set the initial
state of those cellular automata depending on where people are standing in the room...  

# To Use
---------
Set up your Pixelblaze.  Take note of it's IP address.

On your Hubitat Elevation's admin page, select "Drivers Code", then click the
"New Driver" button.  Paste the Groovy driver code from this repository into 
the editor window and click the "SAVE" button.

Create a new virtual device on your Hubitat, name and label it, and select 
"Pixelblaze Controller" as the device type.  Save your new device.

Click the new device on the Hubitat's "Devices" page, and enter your Pixelblaze's
IP address in the provided field.  You will also need to enter the names of the 
Pixelblaze patterns to use for the "On" and "Off" states.  You can use any pattern
stored on your pixelblaze.

For the "Off" pattern, I just wrote a quick pattern that does
nothing when called to render pixels.  Like this:
```javascript
   export function render(index) {
     // hsv(1,0,0)
   }
```
But you can have "On" and "Off" do anything you like!


