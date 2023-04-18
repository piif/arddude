props=/tmp/p
#$(mktemp)

arduino-cli compile --show-properties --fqbn arduino:avr:uno /home/pif/GitHub/piif/ArduinoTests/leds/testLeds.ino > $props
sed  -e "s/=\(.*\)/='\1'/" -e 's/{/${/g' $props > ${props}.e
