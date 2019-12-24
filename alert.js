// Pixelblaze pattern to allow
// testing of SetVariables command
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