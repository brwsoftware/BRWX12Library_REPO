package com.brwsoftware.brwx12library;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

public class X12ConverterXml {

	private static final int HDR_STATE_NONE = 0;
	private static final int HDR_STATE_ISA = 1;
	private static final int HDR_STATE_GS = 2;
	private static final int HDR_STATE_ST = 4;

	public X12ConverterXml() {
		schemaLoopStack = new X12ConverterXml.SchemaLoopStack();
		xmlNodeStack = new X12ConverterXml.XmlNodeStack();
	}

	private final class SchemaLoopStack {
		public SchemaLoopStack() {
			stack = new LinkedList<Item>();
		}

		private final class Item {
			public Item() {
				childLoopCountMap = new HashMap<Integer, Integer>();
			}
			
			private Item parent;
			private X12Schema.Loop loop;
			private int count;
			private String hlId;
			private HashMap<Integer, Integer> childLoopCountMap;
			
			public Item getParentItem() {
				return parent;
			}
			public void setParentItem(Item parent) {
				this.parent = parent;
			}
			public X12Schema.Loop getLoop() {
				return loop;
			}
			public void setLoop(X12Schema.Loop loop) {
				this.loop = loop;
			}
			public int getCount() {
				return count;
			}
			public int incrementCount() {
				return count++;
			}
			public String getHLID() {
				return hlId;
			}
			public boolean hasHLID() {
				return (hlId != null | hlId.length() != 0) ;
			}
			
			void setHLID(X12Segment seg) {
				if(isSegmentHL(seg) && seg.getElementCount() > 1) {
					hlId = seg.getElement(1);
				}
			}
			@SuppressWarnings("unused")
			boolean isEqualParentHLID(X12Segment seg) {
				if(hasHLID() &&
						isSegmentHL(seg) &&
						seg.getElementCount() > 2 &&
						seg.getElement(2).compareToIgnoreCase(getHLID()) == 0) {
					return true;
				}
				return false;				
			}
			int getChildLoopCount(int childLoopIndex) {
				int count = 0;
				if(childLoopCountMap.containsKey(childLoopIndex)) {
					count = childLoopCountMap.get(childLoopIndex);
				}
				return count;
			}
			void incrementChildLoopCount(int childLoopIndex) {
				int count = getChildLoopCount(childLoopIndex) + 1;
				childLoopCountMap.put(childLoopIndex, count);
			}
			void resetChildLoopCounters() {
				childLoopCountMap.clear();
			}
		}
		
		private Deque<Item> stack;
		
		public boolean isEmpty() {
			return stack.isEmpty();
		}
		public void clear() {
			stack.clear();
		}
		Item current() {
			if(stack.isEmpty()) return null;
			return stack.getLast();
		}
		Item push(X12Schema.Loop loop, X12Segment seg) {
			Item item = new Item();
			item.setLoop(loop);
			item.incrementCount();
			
			if(!stack.isEmpty()){
				item.setParentItem(current());
			}
			
			if(seg != null && isSegmentHL(seg)) {
				item.setHLID(seg);
			}
			
			stack.addLast(item);
						
			return item;
		}
		Item pop() {
			return stack.removeLast();
		}
	}

	private final class XmlNodeStack {
		public XmlNodeStack() {
			stack = new ArrayList<XMLStreamWriter>();
		}

		private ArrayList<XMLStreamWriter> stack;

		public int push(XMLStreamWriter writer, String name)
				throws XMLStreamException {
			stack.add(writer);
			writer.writeStartElement(name);
			return stack.size() - 1;
		}

		public void pop() throws XMLStreamException {
			if (!stack.isEmpty()) {
				stack.remove(stack.size() - 1).writeEndElement();
			}
		}

		public void popUntil(int pos) throws XMLStreamException {
			while (!stack.isEmpty()) {
				if ((stack.size() - 1) == pos) {
					break;
				} else {
					pop();
				}
			}
		}

		@SuppressWarnings("unused")
		public void popThrough(int pos) throws XMLStreamException {
			while (!stack.isEmpty()) {
				if ((stack.size() - 1) == pos) {
					pop();
					break;
				} else {
					pop();
				}
			}
		}

		boolean isEmpty() {
			return stack.isEmpty();
		}

		void clear() {
			stack.clear();
		}
	}

	private X12ConverterXml.SchemaLoopStack schemaLoopStack;
	private X12ConverterXml.XmlNodeStack xmlNodeStack;
	private X12Schema.TransactionSet x12SchemaTS;
	private X12Schema x12Schema;
	private int positionISA = -1;
	private int positionGS = -1;
	private int positionST = -1;
	private int hdrState = HDR_STATE_NONE;
	//Note: these were used for error info in the original c++ code
	@SuppressWarnings("unused")
	private int segmentCount = 0;
	@SuppressWarnings("unused")
	//End note
	private String currentSegmentID;
	private char subElementSeparator;
	private XMLStreamWriter xmlWriter;
	private XMLOutputFactory xmlOutputFactory;

	private void initializeState() {
		hdrState = HDR_STATE_NONE;
		segmentCount = 0;
		schemaLoopStack.clear();
		xmlNodeStack.clear();
		x12SchemaTS = null;
	}

	private int pushXmlNode(String name) throws XMLStreamException {
		return xmlNodeStack.push(xmlWriter, name);
	}

	private void popXmlNode() throws XMLStreamException {
		xmlNodeStack.pop();
	}

	private void popXmlNodeUntil(int pos) throws XMLStreamException {
		xmlNodeStack.popUntil(pos);
	}

	private boolean isSegment(X12Segment theSegment, String seg) {
		return ((theSegment.getElementCount() > 0) && theSegment.getElement(0)
				.compareToIgnoreCase(seg) == 0);
	}

	private boolean isSegmentHL(X12Segment theSegment) {
		return isSegment(theSegment, "HL");
	}

	private boolean isValidDetailSegment(X12Segment theSegment) {
		return true;
	}

	private boolean isExpectedSegment(X12Segment theSegment) {
		if (theSegment.getElementCount() == 0) {
			return false;
		}

		boolean bReturn = false;

		if ((hdrState & HDR_STATE_ST) == HDR_STATE_ST) {
			// Must be SE (end of trnx set) or valid detail
			if (isSegment(theSegment, "SE") || isValidDetailSegment(theSegment)) {
				bReturn = true;
			}
		} else if ((hdrState & HDR_STATE_GS) == HDR_STATE_GS) {
			// Must be ST (start of trnx set) or GE(end of function group)
			if (isSegment(theSegment, "ST") || isSegment(theSegment, "GE")) {
				bReturn = true;
			}
		} else if ((hdrState & HDR_STATE_ISA) == HDR_STATE_ISA) {
			// Must be GS (start of function group) or IEA (end of ISA) or TA1
			// Note: If TA1, then it should be the lone record between ISA and
			// IEA
			if (isSegment(theSegment, "GS") || isSegment(theSegment, "IEA")
					|| isSegment(theSegment, "TA1")) {
				bReturn = true;
			}
		} else if (hdrState == HDR_STATE_NONE) {
			if (isSegment(theSegment, "ISA")) {
				return true;
			}
		} else {
			bReturn = false;
		}

		return bReturn;
	}

	boolean isLoopEnder(X12Segment theSegment,
			SchemaLoopStack.Item loopStackItem) {
		String segID = theSegment.getElement(0);
		if (loopStackItem.getLoop().hasEndingSegment()
				&& loopStackItem.getLoop().isEndingSegment(segID)) {
			return true;
		}
		return false;
	}

	boolean isLoopMember(X12Segment theSegment,
			SchemaLoopStack.Item loopStackItem) {
		String segID = theSegment.getElement(0);
		String segData = theSegment.hasElement(1) ? theSegment.getElement(1) : null;
		if (!loopStackItem.getLoop().isStartingSegment(segID, segData)
				&& loopStackItem.getLoop().hasDataSegment(segID)) {
			return true;
		}
		return false;
	}

	boolean isLoopRepeater(X12Segment theSegment,
			SchemaLoopStack.Item loopStackItem) {
		String segID = theSegment.getElement(0);
		String segData = theSegment.hasElement(1) ? theSegment.getElement(1) : null;
		if (loopStackItem.getLoop().isStartingSegment(segID, segData)) {
			if (isSegmentHL(theSegment)) {
				//I think the very nature of HL segments means they are always child starters
				return false;
				//if(loopStackItem.isEqualParentHLID(theSegment))
				//{
				//	bReturn = true;
				//}
			} else if (loopStackItem.getLoop().getRepetition() == X12Schema.INFINITE_REPEAT
					|| loopStackItem.getCount() < loopStackItem.getLoop()
							.getRepetition()) {
				return true;
			}
		}
		return false;
	}

	boolean isLoopChildStarter(X12Segment theSegment,
			SchemaLoopStack.Item loopStackItem) {
		//Is this segment is a starter for the immediate children
		return (getChildLoopIndex(theSegment, loopStackItem) != -1);
	}

	boolean isLoopSiblingStarter(X12Segment theSegment,
			SchemaLoopStack.Item loopStackItem) {

		SchemaLoopStack.Item theParent = loopStackItem.getParentItem();
		if(theParent == null) return false;
		
		return isLoopChildStarter(theSegment, theParent);
	}

	boolean isLoopParent(X12Segment theSegment,
			SchemaLoopStack.Item loopStackItem) {
		return (searchParentLoops(theSegment, loopStackItem) != 0);
	}
	
	int getChildLoopIndex(X12Segment theSegment,
			SchemaLoopStack.Item loopStackItem)
	{
		int nFound = -1;

		String segID = theSegment.getElement(0);
		String segData = theSegment.hasElement(1) ? theSegment.getElement(1) : null;
		int count = loopStackItem.getLoop().getLoopCount();
		
		for(int i = 0; i < count; i++)
		{
			X12Schema.Loop theChildLoop = loopStackItem.getLoop().getLoopAt(i);

			if(theChildLoop.isStartingSegment(segID, segData))
			{
				int nChildCount = loopStackItem.getChildLoopCount(i);
				if(nChildCount == 0
					||
					theChildLoop.getRepetition() == X12Schema.INFINITE_REPEAT
					|| 
					nChildCount < theChildLoop.getRepetition())
				{
					nFound = i;
					break;
				}
			}
		}

		return nFound;
	}
	
	int searchParentLoops(X12Segment theSegment,
			SchemaLoopStack.Item loopStackItem)
	{
		int nFound = 0;
		int nCount = 0;

		SchemaLoopStack.Item parent = loopStackItem.getParentItem();
		while(parent != null && nFound == 0)
		{
			nCount++;

			if(isLoopEnder(theSegment, parent)
				||
				isLoopRepeater(theSegment, parent)
				||
				isLoopChildStarter(theSegment, parent)
				||
				isLoopMember(theSegment, parent))
			{
				nFound = nCount;
			}
			else
			{
				parent = parent.getParentItem();
			}
		}

		return nFound;
	}	
	
	private void openLoop(X12Segment theSegment,
			X12Schema.Loop loop) throws XMLStreamException{
		//Push SchemaLoop onto the stack
		SchemaLoopStack.Item loopStackItem = schemaLoopStack.push(loop, theSegment);

		//Push XmlNode onto the stack
		pushXmlNode("Loop");
		
		xmlWriter.writeAttribute("id", loopStackItem.getLoop().getLoopID());
	}
	
	private void repeatLoop(X12Segment theSegment, SchemaLoopStack.Item loopStackItem) throws XMLStreamException{
		//Pop he current XmlNode
		if(!xmlNodeStack.isEmpty()){
			popXmlNode();
		}
		
		//Increment loop usage count
		loopStackItem.incrementCount();
		
		//Reset child loop counters
		loopStackItem.resetChildLoopCounters();
		
		//Push XmlNode onto the stack
		pushXmlNode("Loop");
		
		xmlWriter.writeAttribute("id", loopStackItem.getLoop().getLoopID());
	}
	
	private void closeLoop(int count) throws XMLStreamException{
		for(int i=0; i < count && !xmlNodeStack.isEmpty(); i++){
			//Pop SchemaLoop from the stack
			schemaLoopStack.pop();
			
			//Pop XmlNode from the stack
			popXmlNode();
		}
	}
	
	private void createSegmentNode(X12Segment theSegment) throws XMLStreamException {
		String name = theSegment.getElement(0);

		xmlWriter.writeStartElement(name);

		for (int i = 1; i < theSegment.getElementCount(); i++) {

			xmlWriter.writeStartElement(String.format("%S%02d", theSegment.getElement(0), i));
			if (hasSubElements(theSegment, i)) {
				createSubElements(theSegment, i);
			} else {
				xmlWriter.writeCharacters(theSegment.getElement(i));
			}
			xmlWriter.writeEndElement();
		}

		xmlWriter.writeEndElement();
	}

	private boolean hasSubElements(X12Segment theSegment, int index) {
		// Do not consider the ISA element
		return (!isSegment(theSegment, "ISA") && theSegment.getElement(index).indexOf(subElementSeparator) >= 0);		
	}
	
	private void createSubElements(X12Segment theSegment, int index) throws XMLStreamException {
		String[] subEle = theSegment.getElement(index).split(Character.toString(subElementSeparator));
		
		for (int i = 0; i < subEle.length; i++) {
			xmlWriter.writeStartElement(String.format("%S%02d-%02d", theSegment.getElement(0), index, i));
			xmlWriter.writeCharacters(subEle[i]);
			xmlWriter.writeEndElement();
		}
	}
	
	private void processSegment(X12Segment theSegment)
			throws XMLStreamException, X12Exception {
		segmentCount++;
		currentSegmentID = theSegment.getElement(0);

		if (isSegment(theSegment, "ISA")) {
			initializeState();
			processSegmentISA(theSegment);
		} else if (isSegment(theSegment, "GS")) {
			processSegmentGS(theSegment);
		} else if (isSegment(theSegment, "ST")) {
			processSegmentST(theSegment);
		} else if (isSegment(theSegment, "SE")) {
			processSegmentSE(theSegment);
		} else if (isSegment(theSegment, "GE")) {
			processSegmentGE(theSegment);
		} else if (isSegment(theSegment, "IEA")) {
			processSegmentIEA(theSegment);
		} else {
			processSegmentDetail(theSegment);
		}
	}

	private void processSegmentISA(X12Segment theSegment) throws XMLStreamException {
		hdrState |= HDR_STATE_ISA;

		// Save the ISA position
		positionISA = pushXmlNode("InterchangeControl");

		createSegmentNode(theSegment);
	}

	private void processSegmentGS(X12Segment theSegment) throws XMLStreamException {
		hdrState |= HDR_STATE_GS;

		// Save the GS position
		positionGS = pushXmlNode("FunctionalGroup");

		createSegmentNode(theSegment);
	}

	private void processSegmentST(X12Segment theSegment) throws X12Exception,
			XMLStreamException {
		if (theSegment.getElementCount() < 2) {
			throw new X12Exception("Unexpected ST segment state");
		}

		hdrState |= HDR_STATE_ST;

		// Save the ST position
		positionST = pushXmlNode("TransactionSet");

		// Write the TransactionSet Identifier attribute
		String tsID = theSegment.getElement(1);
		xmlWriter.writeAttribute("id", tsID);

		// Write the ST node
		createSegmentNode(theSegment);

		 // If we have a schema retrieve the TransactionSet schema and push it on
		 // the schema stack
		if (x12Schema != null) {
			boolean found = false;

			// Attempt to find a implementation specific schema to use
			if (theSegment.hasElement(3)) {
				String tsImpl = theSegment.getElement(3);

				// First try an exact match
				if (x12Schema.hasTransactionSet(tsID, tsImpl)) {
					x12SchemaTS = x12Schema.getTransactionSet(tsID, tsImpl);
					schemaLoopStack.push(x12SchemaTS, null);
					found = true;
				}

				// If not found, look for a partial match on the implementation
				// For example: 005010X223A2 => look for 005010X223
				if (!found && tsImpl.length() > 10) {
					String tsImpl10 = tsImpl.substring(0, 10);

					if (x12Schema.hasTransactionSet(tsID, tsImpl10)) {
						x12SchemaTS = x12Schema.getTransactionSet(tsID, tsImpl10);
						schemaLoopStack.push(x12SchemaTS, null);
						found = true;
					}
				}

			}

			//If not found try a more general version
			if(!found && x12Schema.hasTransactionSet(tsID)) {
				x12SchemaTS = x12Schema.getTransactionSet(tsID);
				schemaLoopStack.push(x12SchemaTS, null);
			}
		}
	}

	private void processSegmentSE(X12Segment theSegment) throws XMLStreamException {
		hdrState ^= HDR_STATE_ST;

		// Pop all XmlNodes up to the TransactionSet
		popXmlNodeUntil(positionST);

		// Write the SE
		createSegmentNode(theSegment);

		// End TransactionSet
		popXmlNode();
	}

	private void processSegmentGE(X12Segment theSegment) throws XMLStreamException {
		hdrState ^= HDR_STATE_GS;

		// Pop all XmlNodes up to the FunctionalGroup
		popXmlNodeUntil(positionGS);

		// Write the GE
		createSegmentNode(theSegment);

		// End FunctionalGroup
		popXmlNode();
	}

	private void processSegmentIEA(X12Segment theSegment) throws XMLStreamException {
		hdrState ^= HDR_STATE_ISA;

		// Pop all XmlNodes up to the InterchangeControl
		popXmlNodeUntil(positionISA);

		// Write the IEA
		createSegmentNode(theSegment);

		// End InterchangeControl
		popXmlNode();
	}

	private void processSegmentDetail(X12Segment theSegment) throws XMLStreamException {
		if (x12SchemaTS != null && !schemaLoopStack.isEmpty()) {
			processSegmentLoop(theSegment);
		} else {
			// Write out in generic fashion
			createSegmentNode(theSegment);
		}
	}

	private void processSegmentLoop(X12Segment theSegment) throws XMLStreamException{
		boolean bProcess = true;
		while(bProcess)
		{
			bProcess = false;
			SchemaLoopStack.Item loopStackItem = schemaLoopStack.current();

			if(isLoopRepeater(theSegment, loopStackItem))
			{
				repeatLoop(theSegment, loopStackItem);
				createSegmentNode(theSegment);
			}
			else if(isLoopChildStarter(theSegment, loopStackItem))
			{
				int nIndex = getChildLoopIndex(theSegment, loopStackItem);
				assert (nIndex > -1);
				loopStackItem.incrementChildLoopCount(nIndex);
				openLoop(theSegment, loopStackItem.getLoop().getLoopAt(nIndex));
				createSegmentNode(theSegment);
			}
			else if(isLoopEnder(theSegment, loopStackItem))
			{
				if(isLoopMember(theSegment, loopStackItem))
				{
					createSegmentNode(theSegment);
				}
				closeLoop(1);
			}
			else if(isLoopSiblingStarter(theSegment, loopStackItem))
			{
				closeLoop(searchParentLoops(theSegment, loopStackItem));
				
				//Process this segment again within the parent loop
				bProcess = true;
			}
			else if(isLoopMember(theSegment, loopStackItem))
			{
				createSegmentNode(theSegment);
			}
			else if(isLoopParent(theSegment, loopStackItem))
			{
				closeLoop(searchParentLoops(theSegment, loopStackItem));

				//Process this segment again within the parent loop
				bProcess = true;
			}
		}
	}
	
	public void convert(InputStream input, OutputStream output) throws XMLStreamException, IOException, X12Exception {
		convert(input, output, null);
	}
	
	public void convert(InputStream input, OutputStream output, X12Schema x12Schema)
			throws XMLStreamException, IOException, X12Exception {
		// Ensure the factory
		if (xmlOutputFactory == null) {
			xmlOutputFactory = XMLOutputFactory.newInstance();
		}

		// Initialize
		initializeState();
		
		// Cache values
		this.x12Schema = x12Schema;

		// Create the XML Writer
		xmlWriter = xmlOutputFactory.createXMLStreamWriter(output);

		// Create the X12Reader
		X12Reader theReader = new X12Reader(input);

		// Read ISA record
		ISASegment theISA = theReader.readISA();

		// Cache the SubElementSeparator
		subElementSeparator = theISA.getComponementSeparator();
		
		// Convert the ISA structure to a segment
		X12Segment theSegment = new X12Segment(theISA.toString().toCharArray(),
				theISA.getElementSeparator(), theISA.getSegmentSeparator());

		if (!isExpectedSegment(theSegment)) {
			throw new X12Exception("Unexpected X12 segment - not ISA");
		}

		// Begin the Xml Doc
		xmlWriter.writeStartDocument();
		xmlWriter.writeStartElement("X12");

		// Process ISA
		processSegment(theSegment);

		// Process segments
		theSegment = theReader.readSegment();
		while (theSegment != null) {

			if (isExpectedSegment(theSegment)) {
				processSegment(theSegment);
			} else {
				throw new X12Exception("Unexpected X12 segment");
			}

			theSegment = theReader.readSegment();
		}

		// Complete the Xml Doc
		xmlWriter.writeEndElement();
		xmlWriter.writeEndDocument();
		xmlWriter.flush();
	}
}
