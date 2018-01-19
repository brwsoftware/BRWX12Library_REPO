package com.brwsoftware.brwx12library;

import java.util.ArrayList;

public final class X12Segment {
	private char[] data;
	private ArrayList<X12Element> elements;
	
	public X12Segment(char[] theSegment, char eleSep, char segSep) throws X12Exception {
		data = theSegment;
		elements = new ArrayList<X12Element>();

		// Start looking for elements
		int curIndex = -1;
		int len = 0;
		for (int i = 0; i < data.length; i++) {
			if (curIndex == -1) {
				curIndex = i;
			}
			if (data[i] == eleSep || data[i] == segSep) {
				elements.add(new X12Element(curIndex, len));
				len = 0;
				curIndex = -1;
			} else {
				len++;
			}
		}
		
		if(elements.isEmpty()){
			throw new X12Exception("Invalid X12 segment");
		}
	}
	
	public String toString() {
		return new String(data);
	}
	
	private final class X12Element {
		private int offset;
		private int length;

		public X12Element(int theOffset, int theLength) {
			offset = theOffset;
			length = theLength;
		}
	}

	public int getElementCount() {
		if (elements == null) {
			return 0;
		}
		return elements.size();
	}

	public boolean hasElement(int index) {
		if (elements == null) {
			return false;
		}
		return (index >= 0 && index < elements.size());
	}

	public String getElement(int index) {
		if (elements == null || data == null) {
			return null;
		}			
		X12Element theEle = elements.get(index);
		return new String(data, theEle.offset, theEle.length);
	}
}
