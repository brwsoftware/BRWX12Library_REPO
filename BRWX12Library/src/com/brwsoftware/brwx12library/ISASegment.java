package com.brwsoftware.brwx12library;

public final class ISASegment {
	private char[] data;
	public static final int ISA_LENGTH = 106;

	public ISASegment(char[] theSegment) throws X12Exception {
		if (theSegment.length != ISA_LENGTH) {
			throw new X12Exception("Invalid ISA length");
		}

		//Test for ISA tag
		if (theSegment[0] != 'I' || theSegment[1] != 'S'
				|| theSegment[2] != 'A') {
			throw new X12Exception("Invalid ISA tag");
		}

		//Test element separators
		char eleSep = theSegment[3];
		if (eleSep != theSegment[6] || eleSep != theSegment[17]
				|| eleSep != theSegment[20] || eleSep != theSegment[31]
				|| eleSep != theSegment[34] || eleSep != theSegment[50]
				|| eleSep != theSegment[53] || eleSep != theSegment[69]
				|| eleSep != theSegment[76] || eleSep != theSegment[81]
				|| eleSep != theSegment[83] || eleSep != theSegment[89]
				|| eleSep != theSegment[99] || eleSep != theSegment[101]
				|| eleSep != theSegment[103]) {
			throw new X12Exception("Invalid ISA element separators");
		}
		
		//Valid ISA
		data = theSegment;
	}

	public String toString() {
		return new String(data);
	}
	
	public char getElementSeparator() {
		return data[3];
	}

	public char getComponementSeparator() {
		return data[104];
	}

	public char getSegmentSeparator() {
		return data[105];
	}

	public String getName() {
		return new String(data, 0, 3);
	}

	public String getAuthorInfoQualifier() {
		return new String(data, 4, 2);
	}

	public String getAuthorInformation() {
		return new String(data, 7, 10);
	}

	public String getSecurityInfoQualifier() {
		return new String(data, 18, 2);
	}

	public String getSecurityInformation() {
		return new String(data, 21, 10);
	}

	public String getInterchangeSenderIDQualifier() {
		return new String(data, 32, 2);
	}

	public String getInterchangeSenderID() {
		return new String(data, 35, 15);
	}

	public String getInterchangeReceiverIDQualifier() {
		return new String(data, 51, 2);
	}

	public String getInterchangeReceiverID() {
		return new String(data, 54, 15);
	}

	public String getInterchangeDate() {
		return new String(data, 70, 6);
	}

	public String getInterchangeTime() {
		return new String(data, 77, 4);
	}

	public char getRepetitionSeparator() {
		return data[82];
	}

	public String getInterchangeControlVersionNumber() {
		return new String(data, 84, 5);
	}

	public String getInterchangeControlNumber() {
		return new String(data, 90, 9);
	}

	public char getAckRequest() {
		return data[100];
	}

	public char getUsageIndcator() {
		return data[102];
	}
}
