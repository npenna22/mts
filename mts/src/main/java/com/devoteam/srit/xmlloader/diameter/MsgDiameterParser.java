/* 
 * Copyright 2012 Devoteam http://www.devoteam.com
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 * 
 * 
 * This file is part of Multi-Protocol Test Suite (MTS).
 * 
 * Multi-Protocol Test Suite (MTS) is free software: you can redistribute
 * it and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of the
 * License.
 * 
 * Multi-Protocol Test Suite (MTS) is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty 
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Multi-Protocol Test Suite (MTS).
 * If not, see <http://www.gnu.org/licenses/>.
 * 
 */

package com.devoteam.srit.xmlloader.diameter;

import com.devoteam.srit.xmlloader.diameter.dictionary.Application;
import com.devoteam.srit.xmlloader.diameter.dictionary.AvpDef;
import com.devoteam.srit.xmlloader.diameter.dictionary.CommandDef;
import com.devoteam.srit.xmlloader.diameter.dictionary.Dictionary;
import com.devoteam.srit.xmlloader.diameter.dictionary.TypeDef;
import com.devoteam.srit.xmlloader.diameter.dictionary.VendorDef;
import com.devoteam.srit.xmlloader.core.exception.ParsingException;
import com.devoteam.srit.xmlloader.core.log.GlobalLogger;
import com.devoteam.srit.xmlloader.core.log.TextEvent.Topic;
import com.devoteam.srit.xmlloader.core.utils.DateUtils;
import com.devoteam.srit.xmlloader.core.utils.UnsignedInt32;
import com.devoteam.srit.xmlloader.core.utils.UnsignedInt64;
import com.devoteam.srit.xmlloader.core.utils.Utils;

import dk.i1.diameter.AVP;
import dk.i1.diameter.AVP_Address;
import dk.i1.diameter.AVP_Float32;
import dk.i1.diameter.AVP_Float64;
import dk.i1.diameter.AVP_Grouped;
import dk.i1.diameter.AVP_Integer32;
import dk.i1.diameter.AVP_Integer64;
import dk.i1.diameter.AVP_OctetString;
import dk.i1.diameter.AVP_Time;
import dk.i1.diameter.AVP_Unsigned32;
import dk.i1.diameter.AVP_Unsigned64;
import dk.i1.diameter.Message;
import dk.i1.diameter.MessageHeader;
import gp.utils.arrays.Array;

import java.net.InetAddress;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.dom4j.Element;

/**
 *
 * @author gpasquiers
 */
public class MsgDiameterParser
{
    
    private static MsgDiameterParser  msgDiameterParser = null;
    
    /** Creates or returns the instance of this stack */
    public static MsgDiameterParser getInstance()
    {
        if (null == msgDiameterParser)
        {
            msgDiameterParser = new MsgDiameterParser();
        }
        return msgDiameterParser;
    }
    
    /** Creates a new Msg object from the root XML element */
    public Message parseMsgFromXml(Boolean request, Element root) throws Exception
    {
        Message msgDiameter = new Message();
        
        // parse the <header> XML tag
        parseMessageHeader(msgDiameter.hdr, request, root);
        
        // Parse recursively the <avp> XML tags
        parseAllAVPs(msgDiameter, root);
        
        return msgDiameter;
    }

    /** Parses then returns the header from the XML root element */
    private void parseMessageHeader(MessageHeader messageHeader, Boolean request, Element root) throws Exception
    {               
        // Parse the header tag
        Element element = root.element("header");
        
        // header tag is mandatory
        if(null == element)
        {
        	throw new ParsingException("The <header> XML tag is mandatory in a DIAMETER message.");
        }
        
        // parse the Application id
        String applicationId = element.attributeValue("applicationId");
        // error if not specified
        if (applicationId == null)
        {
        	throw new ParsingException("There is \"applicationId\" attribute in the <header> XML element.");
        }                
        Application appli = parse_ApplicationId(applicationId, true);
        messageHeader.application_id = appli.get_id();
        
        // parse the Command code
        messageHeader.command_code = parse_CommandCode(element, applicationId);
        
        // Parse the Request : DEPRECATED
    	String strRequest = element.attributeValue("request");
        if (strRequest != null)
        {
        	request = Boolean.parseBoolean(strRequest);
        }
        if(request == null)
        {
        	request = false;
        }
        messageHeader.setRequest(request);
        
        // Parse the Proxyable
        String proxyable = element.attributeValue("proxiable");
        if (proxyable != null)
        {
        	messageHeader.setProxiable(Boolean.parseBoolean(proxyable));
        }
        
        // Parse the Error
        String error = element.attributeValue("error");
        if (error != null)
        {
        	messageHeader.setError(Boolean.parseBoolean(error));
        }
        
        // Parse the Retransmit flag
        String retransmit = element.attributeValue("retransmit");
        if (retransmit != null)
        {
        	messageHeader.setRetransmit(Boolean.parseBoolean(retransmit));
        }
        
        // Parse the Flags : NOT USED
        if (element.attributeValue("flags") != null)
        {
        	throw new ParsingException("Flags attribute is currently not supported in <header> tag");
        }
        
        // Parse the Version : NOT USED        
        if(element.attributeValue("version") != null)
        {
        	throw new ParsingException("Version attribute is currently not supported in <header> tag");
        }
        
        // Parse the EndToEnd
        String endToEnd = element.attributeValue("endToEnd");
        if (endToEnd != null)
        {
        	messageHeader.end_to_end_identifier = Integer.parseInt(endToEnd);
        }
        else
        {
        	messageHeader.end_to_end_identifier = IDProvider.nextId();
        }

        // Parse the HopByHop
        String hopByHop = element.attributeValue("hopByHop");
        if (hopByHop != null)
        {
        	messageHeader.hop_by_hop_identifier = Integer.parseInt(hopByHop);
        }
        else
        {
        	messageHeader.hop_by_hop_identifier = IDProvider.nextId();
        }
    }
        

    /** Parses then returns all the AVPs from the XML root element */
    public void parseAllAVPs(Message message, Element root) throws Exception
    {    
    	List<Element> avpList = root.elements("avp");
	    for(Element avpElement:avpList)
	    {
	    	AVP avp = parseAvp(message, avpElement);
	    	if (avp != null)
	    	{
	    		message.add(avp);
	    	}
	    }
    }
    
    /** Parses an element <avp>; recursively if it contains other AVPs, used by parseMessage */
    private AVP parseAvp(Message message, Element element) throws Exception
    {
        AVP avp;

        String applicationId= Integer.toString(message.hdr.application_id);
                
        // Parse the AVP state flag
        String stateAttr = element.attributeValue("state");
        boolean stateBool = true;
        if (stateAttr != null)
        {
            stateBool = Boolean.parseBoolean(stateAttr);
        }
        if (!stateBool)
        {
        	return null;
        }

        // Parse the AVP Vendor ID
        String vendorIdAttr = element.attributeValue("vendorId");
        VendorDef vendorDef = parse_VendorId(vendorIdAttr, applicationId);
        String vendorId = "0";				// use to make AVP search using vendorId
        if (vendorDef != null)
        {
        	vendorId = Integer.toString(vendorDef.get_code());
        }
        
        // Parse the AVP Code
        AvpDef avpDef = parseAVP_Code(element, applicationId, vendorId);
        int code = -1;
        if (avpDef != null)
        {
        	code = avpDef.get_code();
        }
        else
        {
        	String codeAttr = element.attributeValue("code");
        	code = Integer.parseInt(codeAttr);
        }
        
        // Parse the AVP type
        String type = element.attributeValue("type");
        TypeDef typeDef = parse_AVPType(type, applicationId);
        if (typeDef == null && avpDef != null)
        {
        	typeDef = avpDef.get_type();
        }
        String typeBase = type;        
        if (typeDef != null)
        {
        	type = typeDef.get_type_name();
            while (typeDef.get_type_parent() != null)
            {
                typeDef = typeDef.get_type_parent();
            }
            typeBase = typeDef.get_type_name();
        }
        // default value if the type is not specified and the AVP not nkown in the dictionary
        if (typeBase == null) 
        {
        	typeBase = "OctetString";
        }

        List<Element> listSubAVPs = element.elements("avp");
        // Parse the Grouped AVP
        if (!listSubAVPs.isEmpty())
        {
            // Parse child AVPs
            List<AVP> avpList = new LinkedList<AVP>();        
            for(Element e:listSubAVPs)
            {
    	    	AVP subAvp = parseAvp(message, e);
    	    	if (subAvp != null)
    	    	{
    	    		avpList.add(subAvp);
    	    	}
            }
            
            // Create the AVP and add parsed childs to grouped AVP
            AVP_Grouped gAvp = new AVP_Grouped(code, avpList.toArray(new AVP[0]));
            avp = gAvp ;
        }
        else
        {
            // Parse the AVP value
        	String value = element.attributeValue("value");
            // error if not specified 
            if (value == null)
            {
            	throw new ParsingException("There is no \"value\" attribute in the <avp> XML element : " + element);
            }                

            // Parse the value for the vendorId AVP
            if (type != null && type.equalsIgnoreCase("VendorId"))
            {
            	VendorDef vendorDefValue = parse_VendorId(value, applicationId);
            	value = Integer.toString(vendorDefValue.get_code());            	
            }

            if (type != null && type.equalsIgnoreCase("AppId"))
            {
            	Application applicationValue = parse_ApplicationId(value, false);
            	if (applicationValue != null)
            	{ 
            		value = Integer.toString(applicationValue.get_id());
            	}
            	
            }

            // Create the AVP
            if (typeBase.equalsIgnoreCase("OctetString"))
            {
                try
                {
                    avp = new AVP_OctetString(code, Utils.parseBinaryString(value));
                }
                catch(Exception e)
                {
                    avp = new AVP_OctetString(code, value.getBytes());
                }
            }
            else if (typeBase.equalsIgnoreCase("IPAddress") || typeBase.equalsIgnoreCase("Address"))
            {
            	byte[] val = null;
            	if (value.contains(".") || value.contains(":"))
            	{
            		val = InetAddress.getByName(value).getAddress();
            	}
            	else
            	{
            		Array array = Array.fromHexString(value);
            		val = array.getBytes();
            	}
                avp = new AVP_OctetString(code, val);
            }
            else if (typeBase.equalsIgnoreCase("UTF8String"))
            {
                avp = new AVP_OctetString(code, value.getBytes());
            }
            else if (typeBase.equalsIgnoreCase("Integer32"))
            {
            	value = parse_EnumValue(value, avpDef);
                avp = new AVP_Integer32(code,Integer.parseInt(value));
            }
            else if (typeBase.equalsIgnoreCase("Integer64"))
            {
            	value = parse_EnumValue(value, avpDef);
                avp = new AVP_Integer64(code,Long.parseLong(value));
            }
            else if (typeBase.equalsIgnoreCase("Unsigned32"))
            {
            	value = parse_EnumValue(value, avpDef);
            	UnsignedInt32 unsignedInt32 = new UnsignedInt32(value);
                avp = new AVP_Unsigned32(code,unsignedInt32.intValue());
            }
            else if	(typeBase.equalsIgnoreCase("Unsigned64"))
            {
            	value = parse_EnumValue(value, avpDef);
                UnsignedInt64 unsignedInt64 = new UnsignedInt64(value);
                avp = new AVP_Unsigned64(code,unsignedInt64.longValue());
            }
            else if (typeBase.equalsIgnoreCase("Float32"))
            {
                Float float32 = Float.parseFloat(value);
                avp = new AVP_Float32(code, float32);
            }
            else if	(typeBase.equalsIgnoreCase("Float64"))
            {
                double double64 = Double.parseDouble(value);
                avp = new AVP_Float64(code, double64);
            }
            else if	(typeBase.equalsIgnoreCase("Time"))
            {
            	long time = DateUtils.parseDate(value);
            	Date date = new Date (time);
                avp = new AVP_Time(code, date);
            }
            else
            {
                throw new ParsingException("no matching avp type in protocol stack for type " + type);
            }
        }
        // if vendor Id is not specified then we take it from the avpDef (dictionary)
        if (vendorDef ==  null)
        {
        	if (avpDef !=  null)
        	{
        		vendorDef = avpDef.get_vendor_id();
        	}
        }
        // and set it in the avp object
        avp.vendor_id = 0;
        if (vendorDef !=  null)
        {
        	avp.vendor_id = vendorDef.get_code();
        }
        
        // Parse AVP flags
        String mandatoryAttr = element.attributeValue("mandatory");
        boolean mandatoryBool = Boolean.parseBoolean(mandatoryAttr);
        if (mandatoryAttr == null)
        {
        	if (avpDef != null && "mustnot".equals(avpDef.get_mandatory()))
        	{
        		mandatoryBool = false;
        	}
        	else
        	{
        		mandatoryBool = true;
        	}
        }
        avp.setMandatory(mandatoryBool);
        
        String privateAttr = element.attributeValue("private");
        boolean privateBool = Boolean.parseBoolean(mandatoryAttr);
        if (privateAttr == null)
        {
        	if (avpDef != null && "mustnot".equals(avpDef.get_protected()))
        	{
        		privateBool = false;
        	}
        	else
        	{
        		privateBool = true;
        	}
        }
        avp.setPrivate(privateBool);        
        
        return avp ;
    }
    
    public void doDictionnary(Element root, String applicationId, boolean recurse) throws ParsingException
    {
    	/*
        Application application = Dictionary.getInstance().getApplication(applicationId);
        
        if(null == application)
        {
           //throw new ParsingException("Unknown \"applicationId\" attribute in header: " + applicationId) ;
        }
        
        Element unmodifiedRoot = root.createCopy();
        
        if(root.getName().equalsIgnoreCase("header"))
        {
            //
            // ApplicationId
            //
            String attributeValue;
            
            attributeValue = root.attributeValue("applicationId");
            if(!Utils.isInteger(attributeValue))
            {
                //root.attribute("applicationId").setValue(Integer.toString(application.get_id()));
            }
            
            //
            // CommandCode
            //
            attributeValue = root.attributeValue("command");
            if(!Utils.isInteger(attributeValue))
            {
                CommandDef commandDef = Dictionary.getInstance().getCommandDefByName(attributeValue, applicationId);
                if (commandDef == null)
                {
                	//throw (new ParsingException("Unknown \"command\" attribute in header: " + attributeValue + "skipp it"));
                }
                //root.attribute("command").setValue(Integer.toString(commandDef.get_code()));
            }
            
        }
        else if(root.getName().equalsIgnoreCase("avp"))
        {
            boolean isTypeAppId    = false ;
            boolean isTypeVendorId = false ;
            String attributeValue;
            
            // get the vendorDef object and get the code
            String vendorIdAttr = root.attributeValue("vendorId");
            VendorDef vendorDef = null;
            if(!Utils.isInteger(vendorIdAttr))
            {
            	vendorDef = Dictionary.getInstance().getVendorDefByName(vendorIdAttr, applicationId);
            }
            else
            {
            	int vendorIdCode = Integer.parseInt(vendorIdAttr);
            	vendorDef = Dictionary.getInstance().getVendorDefByCode(vendorIdCode, applicationId);
            }

            ///*
            attributeValue = root.attributeValue("code");
            //
            // Set default values implied by code in XMLTree from dictionnary
            //
            if(null != attributeValue)
            {
            	// get the vendorId code
                String vendorIdCode = "0";
            	if (vendorDef != null)
            	{
            		vendorIdCode = Integer.toString(vendorDef.get_code());
            	}
                AvpDef avpDef = null;
                if(!Utils.isInteger(attributeValue))
                {
                    avpDef = Dictionary.getInstance().getAvpDefByNameVendorIdORName(attributeValue, applicationId, vendorIdCode);
                }
                else
                {
                    avpDef = Dictionary.getInstance().getAvpDefByCodeVendorIdORCode(Integer.parseInt(attributeValue), applicationId, vendorIdCode);
                }

                
                if(null == avpDef)
                {
                    //
                    // If the code value is an integer, we don't necessary have to know it in the dictionnary.
                    // However, if it isn't, we have to.
                    //
                }
                
                //
                // Handle the code attribute
                //
                if(null != avpDef)
                {
                    //root.addAttribute("code", Integer.toString(avpDef.get_code()));
                }
            
                //
                // Handle the type attribute
                //
                if(null == root.attribute("type") && null != avpDef)
                {
                    TypeDef typeDef = avpDef.get_type();
                    if(null != typeDef)
                    {
                        while(null != typeDef.get_type_parent())
                        {
                            if(typeDef.get_type_name().equalsIgnoreCase("AppId"))     isTypeAppId = true;
                            if(typeDef.get_type_name().equalsIgnoreCase("VendorId"))  isTypeVendorId = true;
                            typeDef = typeDef.get_type_parent();
                        }
                        //root.addAttribute("type", typeDef.get_type_name());
                    }
                }
                
                //
                // Handle the vendorId attribute
                //
                if(null == root.attribute("vendorId") && null != avpDef)
                {
                    vendorDef = avpDef.get_vendor_id();
                    if(null != vendorDef)
                    {
                        //root.addAttribute("vendorId", Integer.toString(vendorDef.get_code()));
                    }
                }

                //
                // Handle the mandatory attribute
                //
                if(null == root.attribute("mandatory"))
                {
                    if(null != avpDef && null != avpDef.get_mandatory() && avpDef.get_mandatory().equals("mustnot"))
                    {
                        //root.addAttribute("mandatory", "false");
                    }
                    else
                    {
                        //root.addAttribute("mandatory", "true");
                    }
                }
                
                //
                // Handle the private attribute
                //
                if(null == root.attribute("private") && null != avpDef)
                {
                    if(null != avpDef && null != avpDef.get_protected() && avpDef.get_protected().equals("mustnot"))
                    {
                        //root.addAttribute("private", "false");
                    }
                    else
                    {
                        //root.addAttribute("private", "true");
                    }
                }               
                //
                // Parse the enumerated value that could be present in "value"
                //
                if(null != root.attribute("value") && null != avpDef)
                {
                    String enumName = root.attributeValue("value");
                    long enumValue = avpDef.getEnumCodeByName(enumName);
                    if(enumValue != -1)
                    {
                        //root.attribute("value").setValue(Long.toString(enumValue));
                    }
                }
            }
            else
            {
                throw new ParsingException("in element: " + unmodifiedRoot + "\n" + "code is a mandatory attribute");
            }
            //
            
            //
            // Set the vendorId code (in case it isn't referenced by the avp Code via dictionnary, or overwritten).
            //
            attributeValue = root.attributeValue("vendorId");
            if(null != attributeValue)
            {
                if(!Utils.isInteger(attributeValue))
                {
                    vendorDef = Dictionary.getInstance().getVendorDefByName(attributeValue, applicationId);
                    if(null != vendorDef)
                    {
                        //root.attribute("vendorId").setValue(Integer.toString(vendorDef.get_code()));
                    }
                    else
                    {
                        //throw new ParsingException("in element: " + unmodifiedRoot + "\n" + attributeValue + " is not a valid vendor id in element");
                    }
                }
            }
            
            //
            // Set the top-parent type (in case it isn't referenced by the avp Code via dictionnary, or overwritten).
            //
            if(root.elements().size() > 0)
            {
                root.addAttribute("type", "grouped");
            }

            attributeValue = root.attributeValue("type");
            if(null != attributeValue)
            {
                if(!attributeValue.equalsIgnoreCase("grouped"))
                {
                    if(null != attributeValue)
                    {
                        TypeDef typeDef = Dictionary.getInstance().getTypeDefByName(attributeValue, applicationId);
                        if(null != typeDef)
                        {
                            while(null != typeDef && null != typeDef.get_type_parent())
                            {
                                if(typeDef.get_type_name().equalsIgnoreCase("AppId"))     isTypeAppId = true;
                                if(typeDef.get_type_name().equalsIgnoreCase("VendorId"))  isTypeVendorId = true;
                                typeDef = typeDef.get_type_parent();
                            }
                            //root.attribute("type").setValue(typeDef.get_type_name());
                        }
                        else
                        {
                            throw new ParsingException("In element: " + unmodifiedRoot + "\n" + attributeValue + " is not a valid type");
                        }
                    }

                }
            }
            
            //
            // Handle the value in case it is an appId or vendorId avp, enum should have already been handled at this point
            //
            attributeValue = root.attributeValue("value");
            if(null != attributeValue)
            {
                if(isTypeAppId)
                {
                    Application anApplication = Dictionary.getInstance().getApplication(attributeValue);
                    if(null != anApplication)
                    {
                        //root.attribute("value").setValue(Integer.toString(anApplication.get_id()));
                    }
                }
                if(isTypeVendorId)
                {
                    VendorDef vendorDefValue = Dictionary.getInstance().getVendorDefByName(attributeValue, applicationId);
                    if(null != vendorDefValue)
                    {
                        //root.attribute("value").setValue(Integer.toString(vendorDefValue.get_code()));
                    }
                }
            }
            else
            {
                if(!root.attributeValue("type").equalsIgnoreCase("grouped"))
                {
                    throw new ParsingException("in element: " + unmodifiedRoot + "\n" + "value is a mandatory attribute for element <avp .../> if it is not a grouped avp");
                }
            }
        }
        
        if(recurse)
        {
            List<Element> list = root.elements();
            for(Element element:list)
            {
                doDictionnary(element, applicationId, recurse);
            }
        }
        */
    }
    
    /** 
     * Parses the AVP code from XML element and perform dictionary change 
     */
    private AvpDef parseAVP_Code(Element element, String applicationId, String vendorId) throws Exception
    {    
        String codeAttr = element.attributeValue("code");
        // error if not specified
        if (codeAttr == null)
        {
        	throw new ParsingException("There is \"code\" attribute in the <avp> XML element.");
        }                
	    int pos = codeAttr.lastIndexOf(":");
	    AvpDef avpDef = null;
	    if (pos >= 0)
	    {
	    	String codeLabel = codeAttr.substring(0, pos);
	    	String codeInt = codeAttr.substring(pos + 1);
	    	int code = Integer.parseInt(codeInt);
	        avpDef = Dictionary.getInstance().getAvpDefByCodeVendorIdORCode(code, applicationId, vendorId);
	        if (avpDef != null && !codeLabel.equals(avpDef.get_name()))
	        {
	        	GlobalLogger.instance().getApplicationLogger().warn(Topic.PROTOCOL, 
	        			"For the AVP code, the label \"" + codeLabel + "\" does not match the code \"" + avpDef.get_code() + "\" in the dictionary; " +
	        			"we assume the code is \"" + avpDef.get_code() + " and we are waiting the label \"" + avpDef.get_name() + "\".");

	        }
	    }
	    else
	    {
	        if(!Utils.isInteger(codeAttr))
	        {
	            avpDef = Dictionary.getInstance().getAvpDefByNameVendorIdORName(codeAttr, applicationId, vendorId);
	            if (avpDef == null)
	            {
	            	throw new ParsingException("The avp \"" + codeAttr + "\" is not found in the dictionary.");        	
	            }
	        }
	        else
	        {
	        	int code = Integer.parseInt(codeAttr);
	        	avpDef = Dictionary.getInstance().getAvpDefByCodeVendorIdORCode(code, applicationId, vendorId);
	        }
	    }
	    return avpDef;
    }
 
    /** 
     * Parses the Command code from XML element and perform dictionary change 
     */
    private int parse_CommandCode(Element element, String applicationId) throws Exception
    {    
        String commandAttr = element.attributeValue("command");
        // error if not specified
        if (commandAttr == null)
        {
        	throw new ParsingException("There is no \"command\" attribute in the <header> XML element.");
        }                

	    int pos = commandAttr.lastIndexOf(":");
	    int code = -1;
	    if (pos >= 0)
	    {
	    	String codeLabel = commandAttr.substring(0, pos);
	    	String codeInt = commandAttr.substring(pos + 1);
	    	code = Integer.parseInt(codeInt);
	    	CommandDef commandDef = Dictionary.getInstance().getCommandDefByCode(code, applicationId);
	        if (commandDef != null && !codeLabel.equals(commandDef.get_name()))
	        {
	        	GlobalLogger.instance().getApplicationLogger().warn(Topic.PROTOCOL, 
	        		"For the header command code, the label \"" + codeLabel + "\" does not match the code \"" + commandDef.get_code() + "\" in the dictionary.");
	        }
	    }
	    else
	    {
	        if(!Utils.isInteger(commandAttr))
	        {
	        	CommandDef commandDef = Dictionary.getInstance().getCommandDefByName(commandAttr, applicationId);
	            if (commandDef == null)
	            {
	            	throw new ParsingException("The command code \"" + commandAttr + "\" is not found in the dictionary.");        	
	            }
	            code = commandDef.get_code(); 
	        }
	        else
	        {
	        	code = Integer.parseInt(commandAttr);
	        }
	    }
	    return code;
    }

    /** 
     * Parses the Application Id from XML element and perform dictionary change 
     */
    private Application parse_ApplicationId(String appliIdAttr, boolean checkExist) throws Exception
    {    
    	Application application = null;
	    int pos = appliIdAttr.lastIndexOf(":");
	    if (pos >= 0)
	    {
	    	String codeLabel = appliIdAttr.substring(0, pos);
	    	String codeInt = appliIdAttr.substring(pos + 1);
	    	int code = Integer.parseInt(codeInt);
	    	application = Dictionary.getInstance().getApplicationById(code);
	    	if (application != null && !codeLabel.equals(application.get_name()))
	        {
	        	GlobalLogger.instance().getApplicationLogger().warn(Topic.PROTOCOL, 
	        		"For the header application ID, the label \"" + codeLabel + "\" does not match the id \"" + application.get_id() + "\" in the dictionary.");
	        }
	    }
	    else
	    {
	        if(!Utils.isInteger(appliIdAttr))
	        {
	        	application = Dictionary.getInstance().getApplicationByName(appliIdAttr);
	            // not a problem because Application-Id is also an enumeration AVP
	        	if (application == null)
	            {
	        		if (checkExist)
	        		{
	        			throw new ParsingException("The application id \"" + appliIdAttr + "\" is not found in the dictionary.");
	        		}
	        		else
	        		{
	        			return null;
	        		}
	            } 
	        }
	        else
	        {
	        	int code = Integer.parseInt(appliIdAttr);
	        	application = Dictionary.getInstance().getApplicationById(code);
	        }
	    }
	    return application;
    }

    /** 
     * Parses the AVP Vendor Id from XML element and perform dictionary change 
     */
    private VendorDef parse_VendorId(String vendorIdAttr, String applicationId) throws Exception
    {    
    	if (vendorIdAttr == null)
    	{
    		return null;
    	}
    	
	    int pos = vendorIdAttr.lastIndexOf(":");
	    VendorDef vendorDef = null;
	    if (pos >= 0)
	    {
	    	String codeLabel = vendorIdAttr.substring(0, pos);
	    	String codeInt = vendorIdAttr.substring(pos + 1);
	    	int code = Integer.parseInt(codeInt);
	    	vendorDef = Dictionary.getInstance().getVendorDefByCode(code, applicationId);
	        if (vendorDef != null && !codeLabel.equals(vendorDef.get_vendor_id()))
	        {
	        	GlobalLogger.instance().getApplicationLogger().warn(Topic.PROTOCOL, 
	        		"For the AVP vendor ID, the label \"" + codeLabel + "\" does not match the id \"" + vendorDef.get_code() + "\" in the dictionary.");
	        }
	    }
	    else
	    {
	        if(!Utils.isInteger(vendorIdAttr))
	        {
	        	vendorDef = Dictionary.getInstance().getVendorDefByName(vendorIdAttr, applicationId);
	            if (vendorDef == null)
	            {
	            	throw new ParsingException("The vendor id \"" + vendorIdAttr + "\" is not found in the dictionary.");        	
	            }
	        }
	        else
	        {
	        	int code = Integer.parseInt(vendorIdAttr);
	        	vendorDef = Dictionary.getInstance().getVendorDefByCode(code, applicationId);
	        }
	    }
	    return vendorDef;
    }

    /** 
     * Parses the AVP Enum value from XML element and perform dictionary change 
     */
    private String parse_EnumValue(String enumValue, AvpDef avpDef) throws Exception
    {    
	    int pos = enumValue.lastIndexOf(":");
	    long code;
	    if (pos >= 0)
	    {
	    	String codeLabel = enumValue.substring(0, pos);
	    	String codeLong = enumValue.substring(pos + 1);
	    	code = Long.parseLong(codeLong);
	    	String name = avpDef.getEnumNameByCode(codeLong);
	        if (!codeLabel.equals(name))
	        {
	        	GlobalLogger.instance().getApplicationLogger().warn(Topic.PROTOCOL, 
	        		"For the AVP enumeration value, the label \"" + codeLabel + "\" does not match the code \"" + codeLong + "\" in the dictionary.");
	        }
	    }
	    else
	    {
	        if(!Utils.isInteger(enumValue))
	        {
	        	code = avpDef.getEnumCodeByName(enumValue);
	            if (code < 0)
	            {	            	 
	            	throw new ParsingException("The AVP enum value \"" + enumValue + "\" is not found in the dictionary.");        	
	            } 
	        }
	        else
	        {
	        	code = Long.parseLong(enumValue);
	        }
	    }
	    return Long.toString(code);
    }

    /** 
     * Parses the AVP Vendor Id from XML element and perform dictionary change 
     */
    private TypeDef parse_AVPType(String typeAttr, String applicationId) throws Exception
    {    
    	if (typeAttr == null)
    	{
    		return  null;
    	}
    	TypeDef typeDef = Dictionary.getInstance().getTypeDefByName(typeAttr, applicationId);
        if (typeDef == null && !typeAttr.equalsIgnoreCase("Grouped"))
        {
        	throw new ParsingException("The type id \"" + typeAttr + "\" is not found in the dictionary.");        	
        }
	    return typeDef;
    }

    
}


