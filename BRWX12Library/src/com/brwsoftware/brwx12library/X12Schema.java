package com.brwsoftware.brwx12library;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public class X12Schema {

	public X12Schema() {
		transactionSetMap = new HashMap<String, TransactionSet>();
	}
	
	public static final int INFINITE_REPEAT = -1;
	private HashMap<String, TransactionSet> transactionSetMap;
	private XMLInputFactory xmlInputFactory;

	public final class DataSegmentMap {
		public DataSegmentMap(){
			itemMap = new HashMap<String, Item>();
		}
		
		private final class Item {
			public Item(String id) {
				super();
				this.id = id;
				this.repetition = 1;
			}
			public Item(String id, int repetition) {
				super();
				this.id = id;
				this.repetition = repetition;
			}
			private String id;
			private int repetition;

			@SuppressWarnings("unused")
			public String getId() {
				return id;
			}
			@SuppressWarnings("unused")
			public int getRepetition() {
				return repetition;
			}
		}
		
		private HashMap<String, Item> itemMap;
		
		@SuppressWarnings("unused")
		private void add(String id) {
			itemMap.put(id, new Item(id));
		}
		private void add(String id, int repetition) {
			itemMap.put(id, new Item(id, repetition));
		}
		private boolean has(String id) {
			return itemMap.containsKey(id);
		}
		private Item get(String id) {
			return itemMap.get(id);
		}
	}
	
	public class Loop {
		
		public Loop() {
			dataSegmentMap = new DataSegmentMap();
			loopArray = new ArrayList<Loop>();
			attributes = new Attributes();
		}
		public Loop(Attributes attributes) {
			super();
			this.attributes = attributes;
			dataSegmentMap = new DataSegmentMap();
			loopArray = new ArrayList<Loop>();
		}
		
		public final class Attributes {
			private String loopId;
			private int repetition;
			private String startDataSegment;
			private String endDataSegment;
			
			public String getLoopId() {
				return loopId;
			}
			public void setLoopId(String loopId) {
				this.loopId = loopId;
			}
			public int getRepetition() {
				return repetition;
			}
			public void setRepetition(int repetition) {
				this.repetition = repetition;
			}
			public String getStartDataSegment() {
				return startDataSegment;
			}
			public void setStartDataSegment(String startDataSegment) {
				this.startDataSegment = startDataSegment;
			}
			public String getEndDataSegment() {
				return endDataSegment;
			}
			public void setEndDataSegment(String endDataSegment) {
				this.endDataSegment = endDataSegment;
			}
		}
		
		private DataSegmentMap dataSegmentMap;
		private	ArrayList<Loop> loopArray;
		private Attributes attributes;

		// DataSegment
		public void addDataSegment(String id) {
			addDataSegment(id, 1);
		}		
		public void addDataSegment(String id, int repetition) {
			dataSegmentMap.add(id, repetition);
		}
		public boolean hasDataSegment(String id) {
			return dataSegmentMap.has(id);
		}
		public DataSegmentMap.Item getDataSegment(String id) {
			return dataSegmentMap.get(id);
		}
		
		// Loop Attributes
		protected void setAttributes(Attributes attributes) {
			this.attributes = attributes;
		}
		public String getLoopID() {
			return attributes.loopId;
		}
		public int getRepetition() {
			return attributes.repetition;
		}
		boolean isStartingDataSegment(String id) {
			return (attributes.getStartDataSegment().compareToIgnoreCase(id) == 0);
		}
		boolean hasEndingDataSegment() {
			return attributes.getEndDataSegment() != null && attributes.getEndDataSegment().length() != 0;
		}
		boolean isEndingDataSegment(String id) {
			return (attributes.getEndDataSegment().compareToIgnoreCase(id) == 0);
		}

		// Child Loops
		int getLoopCount() {
			return loopArray.size();
		}
		Loop getLoopAt(int i) {
			return loopArray.get(i);
		}
		Loop newLoop(Attributes theAttr) {
			Loop theLoop = new Loop(theAttr);
			loopArray.add(theLoop);
			return theLoop;
		}
	}
	
	public final class TransactionSet extends X12Schema.Loop {

		public TransactionSet() {
			super();

			Attributes theAttr = new Attributes();
			theAttr.setStartDataSegment("ST");
			theAttr.setEndDataSegment("SE");
			this.setAttributes(theAttr);
		}
		
		public TransactionSet(String id) {
			super();
			this.id = id;

			Attributes theAttr = new Attributes();
			theAttr.setStartDataSegment("ST");
			theAttr.setEndDataSegment("SE");
			this.setAttributes(theAttr);
		}

		private String id;

		public String getId() {
			return id;
		}
	}
	
	private void addTransactionSet(XMLStreamReader xmlReader) throws X12Exception, XMLStreamException {
		// Find the TransactionSetID
		String id = null;
		for(int i=0; i < xmlReader.getAttributeCount(); i++) {
			if(xmlReader.getAttributeLocalName(i).compareToIgnoreCase("id") == 0) {
				id = xmlReader.getAttributeValue(i);
				break;
			}
		}
		
		if(id == null || id.length() == 0) {
			throw new X12Exception("TransactionSet ID not found");
		}
		
		//Ensure the current TransactionSet is cleaned up if its being replaced
		if(hasTransactionSet(id))
		{
			removeTransactionSet(id);
		}
		
		//Create the TransactionSet object
		TransactionSet ts = new TransactionSet(id);
		transactionSetMap.put(id, ts);		
		
		while(xmlReader.hasNext()) {
			int eventType = xmlReader.next();
			if(eventType == XMLStreamConstants.START_ELEMENT) {
				String name = xmlReader.getLocalName();
				if(name.compareToIgnoreCase("Segment") == 0) {
					addDataSegment(ts, xmlReader);
				} else if(name.compareToIgnoreCase("Loop") == 0) {
					addLoop(ts, xmlReader);
				}
			}
		}
	}
	private void addDataSegment(Loop ts, XMLStreamReader xmlReader) throws X12Exception {
		String id = null;
		int repetition = 1;
		for(int i=0; i < xmlReader.getAttributeCount(); i++) {
			String name = xmlReader.getAttributeLocalName(i);
			if(name.compareToIgnoreCase("id") == 0) {
				id = xmlReader.getAttributeValue(i);
			} else if(name.compareToIgnoreCase("Repetition") == 0) {
				repetition = Integer.parseInt(xmlReader.getAttributeValue(i));
			}
		}
		
		if(id == null || id.length() == 0) {
			throw new X12Exception("Segment ID not found");
		}
				
		ts.addDataSegment(id, repetition);
	}
	private void addLoop(Loop currentLoop, XMLStreamReader xmlReader) throws X12Exception, XMLStreamException {
		Loop.Attributes theAttr = currentLoop.new Attributes();
		getLoopAttributes(theAttr, xmlReader);

		if(theAttr.getLoopId() == null || theAttr.getLoopId().length() == 0 || 
				theAttr.getStartDataSegment() == null || theAttr.getStartDataSegment().length() == 0) {		
			throw new X12Exception("Loop attributes not found");
		}

		//Create the loop object
		Loop theNewLoop = currentLoop.newLoop(theAttr);

		boolean continueLoop = true;
		while(continueLoop && xmlReader.hasNext()) {
			switch(xmlReader.next()) {
			case XMLStreamConstants.START_ELEMENT:
				String name = xmlReader.getLocalName();
				if(name.compareToIgnoreCase("Segment") == 0) {
					addDataSegment(theNewLoop, xmlReader);
				} else if(name.compareToIgnoreCase("Loop") == 0) {
					addLoop(theNewLoop, xmlReader);
				}
				break;
			case XMLStreamConstants.END_ELEMENT:
				if(xmlReader.getLocalName().compareToIgnoreCase("Loop") == 0) {
					continueLoop = false;
				}
				break;
			}
		}
	}
	private void getLoopAttributes(Loop.Attributes theAttr, XMLStreamReader xmlReader)
	{
		for(int i=0; i < xmlReader.getAttributeCount(); i++) {
			String name = xmlReader.getAttributeLocalName(i);
			if(name.compareToIgnoreCase("id") == 0) {
				theAttr.setLoopId(xmlReader.getAttributeValue(i));
			} else if(name.compareToIgnoreCase("Repetition") == 0) {
				theAttr.setRepetition(Integer.parseInt(xmlReader.getAttributeValue(i)));
			} else if(name.compareToIgnoreCase("StartSegment") == 0) {
				theAttr.setStartDataSegment(xmlReader.getAttributeValue(i));
			} else if(name.compareToIgnoreCase("EndSegment") == 0) {
				theAttr.setEndDataSegment(xmlReader.getAttributeValue(i));
			}
		}
	}
	
	public void removeTransactionSet(String id) {
		transactionSetMap.remove(id);
	}
	public void removeAllTransactionSets() {
		transactionSetMap.clear();
	}
	public boolean hasTransactionSet(String id) {
		return transactionSetMap.containsKey(id);
	}
	public TransactionSet getTransactionSet(String id) {
		return transactionSetMap.get(id);
	}
	public void addTransactionSet(InputStream theStream) throws XMLStreamException, X12Exception {
		// Ensure the factory
		if (xmlInputFactory == null) {
			xmlInputFactory = XMLInputFactory.newInstance();
		}
		
		XMLStreamReader xmlReader = xmlInputFactory.createXMLStreamReader(theStream);
		
		while(xmlReader.hasNext()) {
			int eventType = xmlReader.next();
			if(eventType == XMLStreamConstants.START_ELEMENT) {
				addTransactionSet(xmlReader);
			}
		}
	}

}