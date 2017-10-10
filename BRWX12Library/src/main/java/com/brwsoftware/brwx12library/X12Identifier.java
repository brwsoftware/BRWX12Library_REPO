package com.brwsoftware.brwx12library;

import java.io.IOException;
import java.io.InputStream;

public class X12Identifier {

	public static class TransactionSet {
		
		private String tsID;
		private String tsImpl;
		public TransactionSet() {
			
		}
		public String getID() {
			return tsID;
		}
		public String getImplementation() {
			return tsImpl;
		}
	}

	public static class Attributes {
		private boolean isX12 = false;
		private final TransactionSet transactionSet = new TransactionSet();
		
		public Attributes() {
			
		}
		public boolean isX12() {
			return isX12;
		}
		
		public TransactionSet getTransactionSet() {
			return transactionSet;
		}
	}

	private X12Identifier() {
	}

	public static Attributes getAttributes(InputStream theStream) throws IOException, X12Exception {
		Attributes theAttributes = new Attributes();
		
		X12Reader theReader = new X12Reader(theStream);
		
		try
		{
			theReader.readISA();
			theAttributes.isX12 = true;
		} catch (X12Exception e) {
			theAttributes.isX12 = false;
		}

		boolean isTA1 = false;
		boolean isST = false;
		X12Segment theSegment =  theReader.readSegment();
		while(theSegment != null) {
			if(theSegment.getElementCount() > 0) {
				if(theSegment.getElement(0).equalsIgnoreCase("TA1")) {
					isTA1 = true;
				}
				else if(theSegment.getElement(0).equalsIgnoreCase("ST")) {
					if(theSegment.getElementCount() > 1) {
						isST = true;
						theAttributes.transactionSet.tsID = theSegment.getElement(1);
						
						if(theSegment.hasElement(3)) {
							theAttributes.transactionSet.tsImpl = theSegment.getElement(3);
						}
						break;
					}
				}
			}
			theSegment =  theReader.readSegment();
		}
		
		if(!isST && isTA1) {
			//No ST, but has a TA1 - Identify this unique situation as a TA1 transaction
			theAttributes.transactionSet.tsID = "TA1";
		}
		
		return theAttributes;
	}
}
