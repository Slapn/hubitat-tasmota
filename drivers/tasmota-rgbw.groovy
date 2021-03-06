/**
 *  Copyright 2016 Eric Maycock. Jules Taplin and Damon Dinsmore
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Sonoff Wifi Switch
 *
 *  Author: Eric Maycock (erocm123)
 *  Date: 2016-06-02
 */

import groovy.json.JsonSlurper
import groovy.util.XmlSlurper

metadata {
	definition (name: "Tasmota RGBW or WWBulb", namespace: "uk.org.ourhouse", author: "Jules Taplin") {
        //capability "Actuator"
		capability "Switch"
		//capability "Refresh"
		capability "ColorControl"
		capability "ColorMode"
		capability "ColorTemperature"
		capability "Light"
		capability "Switch"
		capability "SwitchLevel"
		//capability "Sensor"
        //capability "Configuration"
        //capability "Health Check"

        //command "reboot"

        attribute   "needUpdate", "string"
        attribute   "uptime", "string"
        attribute   "ip", "string"
		attribute	"level", "number" //added by damondins
	}

	simulator {
	}
    /*
    preferences {
        input description: "Once you change values on this page, the corner of the \"configuration\" icon will change orange until all configuration parameters are updated.", title: "Settings", displayDuringSetup: false, type: "paragraph", element: "paragraph"
		generate_preferences(configuration_model())
	}
*/
	preferences {		section("Sonoff Host") {
            input(name: "ipAddress", type: "string", title: "IP Address", displayDuringSetup: true, required: true)
			input(name: "TWlow", type: "string", title: "Tuneable White Low Level", displayDuringSetup: true, required: false)
			input(name: "TWhigh", type: "string", title: "Tuneable White High Level", displayDuringSetup: true, required: false)
	}}
	
	//tileAttribute ("device.level", key: "SLIDER_CONTROL") {
    //    attributeState "level", action:"setLevel"
    //}
/*
 	tiles (scale: 2){
		multiAttributeTile(name:"switch", type: "generic", width: 6, height: 4, canChangeIcon: true){
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label:'${name}', action:"switch.off", backgroundColor:"#00a0dc", icon: "st.switches.switch.on", nextState:"turningOff"
				attributeState "off", label:'${name}', action:"switch.on", backgroundColor:"#ffffff", icon: "st.switches.switch.off", nextState:"turningOn"
				attributeState "turningOn", label:'${name}', action:"switch.off", backgroundColor:"#00a0dc", icon: "st.switches.switch.off", nextState:"turningOff"
				attributeState "turningOff", label:'${name}', action:"switch.on", backgroundColor:"#ffffff", icon: "st.switches.switch.on", nextState:"turningOn"
			}
        }

		standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
		}
        valueTile("ip", "ip", width: 2, height: 1) {
    		state "ip", label:'IP Address\r\n${currentValue}'
		}
        valueTile("uptime", "uptime", width: 2, height: 1) {
    		state "uptime", label:'Uptime ${currentValue}'
		}

    }
*/

	main(["switch"])
	details(["switch",
             "refresh","configure","reboot",
             "ip", "uptime"])
}

def installed() {
	log.debug "installed()"
	configure()
}

def configure() {
    logging("configure()", 1)
    def cmds = []
    cmds = update_needed_settings()
    cmds << getAction("/info")
    if (cmds != []) cmds
}

def updated()
{
    logging("updated()", 1)
    def cmds = []
    cmds = update_needed_settings()
    sendEvent(name: "checkInterval", value: 2 * 15 * 60 + 2 * 60, displayed: false, data: [protocol: "lan", hubHardwareId: device.hub.hardwareID])
    sendEvent(name:"needUpdate", value: device.currentValue("needUpdate"), displayed:false, isStateChange: true)
    if (cmds != []) cmds
}

private def logging(message, level) {
    if (logLevel != "0"){
    switch (logLevel) {
       case "1":
          if (level > 1)
             log.debug "$message"
       break
       case "99":
          log.debug "$message"
       break
    }
    }
}

def parse(description) {
    def events = []
    def msg = parseLanMessage(description)
    def descMap = parseDescriptionAsMap(description)
    def body

    if (descMap["mac"] != null && (!state.mac || state.mac != descMap["mac"])) {
		log.debug "Mac address of device found ${descMap["mac"]}"
        state.mac = descMap["mac"]
	}

    if (state.mac != null && state.dni != state.mac) state.dni = setDeviceNetworkId(state.mac)
    if (descMap["body"]) body = new String(descMap["body"].decodeBase64())

    if (body && body != "") {

    if(body.startsWith("{") || body.startsWith("[")) {
    def slurper = new JsonSlurper()
    def result = slurper.parseText(body)

    log.debug "result: ${result}"

    if (result.containsKey("type")) {
        if (result.type == "configuration")
            events << update_current_properties(result)
    }
    if (result.containsKey("power")) {
        events << createEvent(name: "switch", value: result.power)
    }
    if (result.containsKey("uptime")) {
        events << createEvent(name: "uptime", value: result.uptime, displayed: false)
    }
    if (result.containsKey("deviceType")) {
        state.type = result.deviceType
    }
    } else {
        //log.debug "Response is not JSON: $body"
    }
    }

    if (!device.currentValue("ip") || (device.currentValue("ip") != getDataValue("ip"))) events << createEvent(name: 'ip', value: getDataValue("ip"))

    return events
}

def parseDescriptionAsMap(description) {
	description.split(",").inject([:]) { map, param ->
		def nameAndValue = param.split(":")

        if (nameAndValue.length == 2) map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
        else map += [(nameAndValue[0].trim()):""]
	}
}

def on() {
	log.debug "on()"
    def cmds = []
	sendEvent(name: "switch", value: "on");
    cmds << getAction("Power%20On")
    return cmds
}

def off() {
    log.debug "off()"
	def cmds = []
	sendEvent(name: "switch", value: "off");
    cmds << getAction("Power%20Off")
    return cmds
}

def refresh() {
	log.debug "refresh()"
    def cmds = []
    cmds << getAction("info")
    cmds << getAction("status")
    return cmds
}

def ping() {
    log.debug "ping()"
    refresh()
}

//changed 1/29/2019 by damondins
def setColorTemperature(value)
{
	state.colorMode = "CT"
	sendEvent(name: "colorMode", value: "CT");
	log.debug "ColorTemp = " + value
	def intvalue = value.toInteger()
	def intTWlow = TWlow.toInteger()
	def intTWhigh = TWhigh.toInteger()
	
	def kelvindiff = intTWhigh - intTWlow
	def tasmotadiff = 348
	def kelvinscale = kelvindiff / tasmotadiff
	kelvinscale = Math.round(kelvinscale)
	convertedvalue =  intvalue / kelvinscale
	flipvalue = 153 + (500 - convertedvalue) 
	flipvalue = Math.round(flipvalue)
	
	if (flipvalue < 153) {
		sendEvent(name: "Temp", value: 153);
		cmds << getAction("CT%20" + 153);
	} else if (flipvalue > 500) {
		sendEvent(name: "Temp", value: 500);
		cmds << getAction("CT%20" + 500);
	} else {
		sendEvent(name: "Temp", value: value);
		cmds << getAction("CT%20" + flipvalue);
	}
}

//added by damondins
def setLevel(value) {
	sendEvent(name: "level", value: value);
	cmds << getAction("Dimmer%20" + value);
}

def setColor(value) {
	log.debug "HSVColor = "+ value
	   if (value instanceof Map) {
        def h = value.containsKey("hue") ? value.hue : null
        def s = value.containsKey("saturation") ? value.saturation : null
        def b = value.containsKey("level") ? value.level : null
    	setHsb(h, s, b)
    } else {
        log.warn "Invalid argument for setColor: ${value}"
    }
}

def setHsb(h,s,b)
{
	log.debug("setHsb - ${h},${s},${b}")
	myh = h*4
	if( myh > 360 ) { myh = 360 }
	hsbcmd = "hsbcolor+${myh},${s},${b}"
	log.debug "Cmd = ${hsbcmd}"
	state.hue = h
	state.saturation = s
	state.level = b
	state.colorMode = "RGB"
	sendEvent(name: "hue", value: h);
    sendEvent(name: "saturation", value: s);
	sendEvent(name: "level", value: b);
	sendEvent(name: "colorMode", value: "RGB");
	getAction(hsbcmd)

}

def setHue(h)
{
    setHsb(h,state.saturation,state.level)
}

def setLevel(v,duration)
{
	if(state.colorMode == "RGB") {
		setHsb(state.hue,state.saturation,v)
	}
	else
	{
		state.level = v
		sendEvent(name: "level", value: v);
		getAction("white+${v}")
	}
}

def setSaturation(s)
{
	setHsb(state.hue,s,state.level)
}


private getAction(uri){
  updateDNI()
  def userpass
  def response
  if(password != null && password != "")
      userpass = encodeCredentials("admin", password)

  def headers = getHeader(userpass)

  def params = [
    uri: "http://${getHostAddress()}/ax?c1=${uri}",
    headers: headers
  ]
	log.debug "http://${getHostAddress()}/ax?c1=${uri}"
  httpGet(params) { resp ->
    response = resp.data
  }

  return parseResponse(response)
}

private postAction(uri, data){
  updateDNI()
  def userpass
  def response
  if(password != null && password != "")
      userpass = encodeCredentials("admin", password)

  def headers = getHeader(userpass)

  def params = [
    uri: "http://${getHostAddress()}${uri}",
    headers: headers
  ]

  httpPost(params) { resp ->
    response = resp.data
  }

  parseResponse(response)
}

private setDeviceNetworkId(ip, port = null){
    def myDNI
    if (port == null) {
        myDNI = ip
    } else {
  	    def iphex = convertIPtoHex(ip)
  	    def porthex = convertPortToHex(port)
        myDNI = "$iphex:$porthex"
    }
    log.debug "Device Network Id set to ${myDNI}"
    return myDNI
}

private updateDNI() {
    if (state.dni != null && state.dni != "" && device.deviceNetworkId != state.dni) {
       device.deviceNetworkId = state.dni
    }
}

private getHostAddress() {
    if (override == "true" && ipAddress != null && ipAddress != ""){
        return "${ipAddress}:80"
    }
    else if(getDeviceDataByName("ipAddress") && getDeviceDataByName("port")){
        return "${getDeviceDataByName("ipAddress")}:${getDeviceDataByName("port")}"
    }else{
	    return "${ipAddress}:80"
    }
}

private String convertIPtoHex(ipAddress) {
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
    return hex
}

private String convertPortToHex(port) {
	String hexport = port.toString().format( '%04x', port.toInteger() )
    return hexport
}

private encodeCredentials(username, password){
	def userpassascii = "${username}:${password}"
    def userpass = "Basic " + userpassascii.bytes.encodeBase64().toString()
    return userpass
}

private getHeader(userpass = null){
    def headers = [:]
    headers.put("Host", getHostAddress())
    headers.put("Content-Type", "application/x-www-form-urlencoded")
    if (userpass != null)
       headers.put("Authorization", userpass)
    return headers
}

def reboot() {
	log.debug "reboot()"
    def uri = "/reboot"
    getAction(uri)
}

def sync(ip, port) {
    def existingIp = getDataValue("ip")
    def existingPort = getDataValue("port")
    if (ip && ip != existingIp) {
        updateDataValue("ip", ip)
        sendEvent(name: 'ip', value: ip)
    }
    if (port && port != existingPort) {
        updateDataValue("port", port)
    }
}

def generate_preferences(configuration_model)
{
    def configuration = new XmlSlurper().parseText(configuration_model)

    configuration.Value.each
    {
        if(it.@hidden != "true" && it.@disabled != "true"){
        switch(it.@type)
        {
            case ["number"]:
                input "${it.@index}", "number",
                    title:"${it.@label}\n" + "${it.Help}",
                    range: "${it.@min}..${it.@max}",
                    defaultValue: "${it.@value}",
                    displayDuringSetup: "${it.@displayDuringSetup}"
            break
            case "list":
                def items = []
                it.Item.each { items << ["${it.@value}":"${it.@label}"] }
                input "${it.@index}", "enum",
                    title:"${it.@label}\n" + "${it.Help}",
                    defaultValue: "${it.@value}",
                    displayDuringSetup: "${it.@displayDuringSetup}",
                    options: items
            break
            case ["password"]:
                input "${it.@index}", "password",
                    title:"${it.@label}\n" + "${it.Help}",
                    displayDuringSetup: "${it.@displayDuringSetup}"
            break
            case "decimal":
               input "${it.@index}", "decimal",
                    title:"${it.@label}\n" + "${it.Help}",
                    range: "${it.@min}..${it.@max}",
                    defaultValue: "${it.@value}",
                    displayDuringSetup: "${it.@displayDuringSetup}"
            break
            case "boolean":
               input "${it.@index}", "boolean",
                    title:"${it.@label}\n" + "${it.Help}",
                    defaultValue: "${it.@value}",
                    displayDuringSetup: "${it.@displayDuringSetup}"
            break
        }
        }
    }
}

 /*  Code has elements from other community source @CyrilPeponnet (Z-Wave Parameter Sync). */

def update_current_properties(cmd)
{
    def currentProperties = state.currentProperties ?: [:]
    currentProperties."${cmd.name}" = cmd.value

    if (settings."${cmd.name}" != null)
    {
        if (settings."${cmd.name}".toString() == cmd.value)
        {
            sendEvent(name:"needUpdate", value:"NO", displayed:false, isStateChange: true)
        }
        else
        {
            sendEvent(name:"needUpdate", value:"YES", displayed:false, isStateChange: true)
        }
    }
    state.currentProperties = currentProperties
}


def update_needed_settings()
{
    def cmds = []
    def currentProperties = state.currentProperties ?: [:]

    def configuration = new XmlSlurper().parseText(configuration_model())
    def isUpdateNeeded = "NO"

    cmds << getAction("/configSet?name=haip&value=${device.hub.getDataValue("localIP")}")
    cmds << getAction("/configSet?name=haport&value=${device.hub.getDataValue("localSrvPortTCP")}")

    configuration.Value.each
    {
        if ("${it.@setting_type}" == "lan" && it.@disabled != "true"){
            if (currentProperties."${it.@index}" == null)
            {
                if (it.@setonly == "true"){
                    logging("Setting ${it.@index} will be updated to ${it.@value}", 2)
                    cmds << getAction("/configSet?name=${it.@index}&value=${it.@value}")
                } else {
                    if (it.@index == "externaltype") {
                        if(state.type != "Sonoff S20") {
                            isUpdateNeeded = "YES"
                            logging("Current value of setting ${it.@index} is unknown", 2)
                            cmds << getAction("/configGet?name=${it.@index}")
                        } else {
                            log.debug "Sonoff S20 does not support externatype configuration"
                        }
                    } else {
                        isUpdateNeeded = "YES"
                        logging("Current value of setting ${it.@index} is unknown", 2)
                        cmds << getAction("/configGet?name=${it.@index}")
                    }
                }
            }
            else if ((settings."${it.@index}" != null || it.@hidden == "true") && currentProperties."${it.@index}" != (settings."${it.@index}"? settings."${it.@index}".toString() : "${it.@value}"))
            {
                isUpdateNeeded = "YES"
                logging("Setting ${it.@index} will be updated to ${settings."${it.@index}"}", 2)
                cmds << getAction("/configSet?name=${it.@index}&value=${settings."${it.@index}"}")
            }
        }
    }

    sendEvent(name:"needUpdate", value: isUpdateNeeded, displayed:false, isStateChange: true)
    return cmds
}

def parseResponse(description) {
	//log.debug "Parsing: ${description}"
	//return 				// DISABLED FOR NOW!
    def events = []
    def result = description
    log.debug "result: ${result}"
    if (result != []){
    if (result.containsKey("type")) {
        if (result.type == "configuration")
            events << update_current_properties(result)
    }
    if (result.containsKey("power")) {
        events << createEvent(name: "switch", value: result.power)
    }
    if (result.containsKey("uptime")) {
        events << createEvent(name: "uptime", value: result.uptime, displayed: false)
    }
    if (result.containsKey("deviceType")) {
        state.type = result.deviceType
    }
    }


    if (!device.currentValue("ip") || (device.currentValue("ip") != getDataValue("ip"))) events << createEvent(name: 'ip', value: getDataValue("ip"))
    events.each {
        sendEvent(it)
    }
    return events
}

def configuration_model()
{
'''
<configuration>
<Value type="text" index="ip" label="IP Address" setting_type="preference"></Value>
<Value type="list" index="logLevel" label="Debug Logging Level?" value="0" setting_type="preference" fw="">
<Help>
</Help>
    <Item label="None" value="0" />
    <Item label="Reports" value="1" />
    <Item label="All" value="99" />
</Value>
</configuration>
'''
}