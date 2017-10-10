package com.brwsoftware.brwx12library;

import java.io.BufferedReader;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

public class X12Reader {
	private char elementSeparator = '*';
	private char segmentSeparator = '~';
	private static final char CR = '\r';
	private static final char LF = '\n';
	private static final int ANALYZE_BUFSIZE = 135;
	private BufferedReader bufReader;
	private SegmentWriter bufWriter;

	private enum ReadModifier {
		none, crlf80, crlf133
	};

	private ReadModifier readModifier = ReadModifier.none;
	
	private final class SegmentWriter extends CharArrayWriter {
		private SegmentWriter(int initialSize){
			super(initialSize);
		}
		private boolean IsNameEqual(String theName, char eleSep) {
			synchronized (lock) {
				if (buf.length > theName.length()) {
					for (int i = 0; i < theName.length(); i++) {
						if (buf[i] != theName.charAt(i)) {
							return false;
						}
					}
					// Made it here - characters match so far.
					// If the next char is the element separator then consider
					// it equal
					if (buf[theName.length()] == eleSep) {
						return true;
					}
				}
				return false;
			}
		}

		private void setWritePosition(int pos) {
			if ((pos < 0) || (pos > count)) {
				throw new IndexOutOfBoundsException();
			}
			count = pos;
		}
	}
	public X12Reader(InputStream theStream) {
		bufReader = new BufferedReader(new InputStreamReader(theStream));
	}

	public void close() throws IOException {
		if(bufReader != null) {
			bufReader.close();
			bufReader = null;
		}
	}
	
	public ISASegment readISA() throws IOException, X12Exception {
		char[] theBuf = new char[ANALYZE_BUFSIZE];
		boolean isaIdentified = false;

		//Read past any whitespace
		while (true) {
			int theChar = bufReader.read();
			if (theChar == -1) {
				throw new X12Exception("Error positioning ISA");
			}
			if (Character.isLetterOrDigit(theChar)) {
				theBuf[0] = (char) theChar;

				//Read remaining ISA characters
				if (bufReader.read(theBuf, 1, ISASegment.ISA_LENGTH - 1) != (ISASegment.ISA_LENGTH - 1)) {
					throw new X12Exception("Invalid ISA length");
				}
				break;
			}
		}
		
		//Test for ISA tag
		if (theBuf[0] != 'I' || theBuf[1] != 'S' || theBuf[2] != 'A') {
			throw new X12Exception("Invalid ISA tag");
		}
		
		//Limited test for element separators (don't go past 80 chars)
		if (IsValidISAElementSeparators1(theBuf) == false) {
			throw new X12Exception("Invalid ISA element separators");
		}
		
		//Test elements separators after 80
		if (IsValidISAElementSeparators2(theBuf) == true) {
			isaIdentified = true;
		} else {
			// Test CRLF80 scenario
			if (theBuf[80] == CR && theBuf[81] == LF) {
				//Shifts chars after the CRLF 
				for(int i = 80; i < (ISASegment.ISA_LENGTH - 3); i++) {
					theBuf[i] = theBuf[i + 2];
				}
				//Read next 2 characters
				if (bufReader.read(theBuf, 104, 2) != 2) {
					throw new X12Exception("Invalid ISA length");
				}
				//Test for valid ISA
				if (IsValidISAElementSeparators2(theBuf) == true) {
					readModifier = ReadModifier.crlf80;
					isaIdentified = true;
				}
			} else {
				// Read next 29 characters to test for the CRLF133 scenario
				if (bufReader.read(theBuf, 106, 29) != 29) {
					throw new X12Exception("Invalid ISA length");
				}
				if (theBuf[133] == CR && theBuf[134] == LF) {
					// Between 80 and the end should be all spaces
					boolean bAllSpaces = true;
					for (int i = 80; i < (ANALYZE_BUFSIZE - 2); i++) {
						if (theBuf[i] != 0x20) {
							bAllSpaces = false;
							break;
						}
					}

					if (bAllSpaces) {					
						// Read another 26 chars to complete the ISA
						if (bufReader.read(theBuf, 80, 26) != 26) {
							throw new X12Exception("Invalid ISA length");
						}
						//Test for valid ISA
						if (IsValidISAElementSeparators2(theBuf) == true) {
							readModifier = ReadModifier.crlf133;
							isaIdentified = true;
						}
					}
				}
			}			
		}
		
		if(isaIdentified == false){
			throw new X12Exception("Unknown X12 data");
		}
		
		//Looks like valid ISA
		ISASegment theISA = new ISASegment(Arrays.copyOfRange(theBuf, 0, ISASegment.ISA_LENGTH));

		elementSeparator = theISA.getElementSeparator();
		segmentSeparator = theISA.getSegmentSeparator();
	
		return theISA;
	}

	private boolean IsValidISAElementSeparators1(char[] theBuf) {
		char eleSep = theBuf[3];
		if (eleSep != theBuf[6] || eleSep != theBuf[17] || eleSep != theBuf[20]
				|| eleSep != theBuf[31] || eleSep != theBuf[34]
				|| eleSep != theBuf[50] || eleSep != theBuf[53]
				|| eleSep != theBuf[69] || eleSep != theBuf[76]) {
			return false;
		}
		return true;
	}

	private boolean IsValidISAElementSeparators2(char[] theBuf) {
		char eleSep = theBuf[3];
		if (eleSep != theBuf[81] || eleSep != theBuf[83]
				|| eleSep != theBuf[89] || eleSep != theBuf[99]
				|| eleSep != theBuf[101] || eleSep != theBuf[103]) {
			return false;
		}
		return true;
	}

	public X12Segment readSegment() throws IOException, X12Exception {
		return readSegment(null);
	}
	
	public X12Segment readSegment(String name) throws IOException, X12Exception {
		if (bufWriter == null) {
			bufWriter = new SegmentWriter(256);
		} else {
			bufWriter.reset();
		}

		boolean seenCR = false;
		boolean hasSegment = false;
		boolean endOfStream = false;
		X12Segment theSegment = null;

		// Read the X12 stream one char at a time looking for SegmentSeparator
		while (true) {
			int theChar = bufReader.read();
			if (theChar == -1) {
				endOfStream = true;
				break;
			} else {
				if (bufWriter.size() == 0) {
					// Skip any leading whitespace
					if (Character.isLetterOrDigit(theChar)) {
						bufWriter.write(theChar);
					}
				} else if (theChar == segmentSeparator
						|| (theChar != CR && theChar != LF && theChar != 0)) {
					bufWriter.write(theChar);
				}

				if (readModifier == ReadModifier.crlf133) {
					if (theChar == CR) {
						seenCR = true;
					} else if (seenCR && theChar == LF && bufWriter.size() >= 53) {
						// Move the write position back to exclude all the
						// spaces between 80 and 133
						bufWriter.setWritePosition(bufWriter.size() - 53);
					}
				}

				if (theChar == segmentSeparator) {
					if (name == null) {
						hasSegment = true;
						break;
					} else if (bufWriter.IsNameEqual(name, elementSeparator)) {
						hasSegment = true;
						break;
					} else {
						bufWriter.reset();
					}
				}
			}
		}

		if (bufWriter.size() > 0) {
			if (hasSegment) {
				theSegment = new X12Segment(bufWriter.toCharArray(),
						elementSeparator, segmentSeparator);
			} else if (endOfStream == false) {
				// We have data but never saw the segmentSeparator
				// Note: if we hit the end of stream and we have data but not a
				// segment it could be the remaining spaces of the crlf133 scenario. 
				// We are not going to throw any exceptions for that.
				throw new X12Exception("Invalid X12 segment");
			}
		}

		return theSegment;
	}
}
