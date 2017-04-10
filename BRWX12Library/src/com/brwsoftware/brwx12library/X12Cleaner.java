package com.brwsoftware.brwx12library;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

public class X12Cleaner {
	
/*
	Note: The X12Cleaner class is kind of a left over.
	At one time this class was being used to address situations where
	the X12 data was in lines of 80 or 132. The X12Reader now handles
	those situations. However, if there is a need to write an oddly
	shaped X12 file to one that is continuous characters then this 
	class can be used.
*/	
	private static final int ANALYZE_BUFSIZE = 135;
	private enum State
	{
		unknown,
		noAction,
		crlf80,
		crlf133		
	};
	private State state = State.unknown;
	
	public X12Cleaner() {
	}
	
	public boolean needClean(InputStream input) throws IOException, X12Exception {
		
		//Simple test to check for cleanup situations:
		//	80 character lines
		//  132 character lines
		
		//Note: The state or position of the input stream will be unknown upon return.
		//		So plan accordingly.
		
		state = State.unknown;
		InputStreamReader reader = new InputStreamReader(input);
		
		char[] theBuf = new char[ANALYZE_BUFSIZE];

		// Read past any whitespace
		while (true) {
			int theChar = reader.read();
			if (theChar == -1) {
				throw new X12Exception("Unexpected end of input");
			}
			if (Character.isLetterOrDigit(theChar)) {
				theBuf[0] = (char) theChar;

				if (reader.read(theBuf, 1, ANALYZE_BUFSIZE - 1) != (ANALYZE_BUFSIZE - 1)) {
					throw new X12Exception("Not enough characters");
				}
				break;
			}
		}		
		
		//Test for ISA tag
		if (theBuf[0] != 'I' || theBuf[1] != 'S' || theBuf[2] != 'A') {
			throw new X12Exception("Invalid ISA tag");
		}
		
		//Limited test for element separators (don't go past 80 chars)
		char eleSep = theBuf[3];
		if (eleSep != theBuf[6] || eleSep != theBuf[17] || eleSep != theBuf[20]
				|| eleSep != theBuf[31] || eleSep != theBuf[34]
				|| eleSep != theBuf[50] || eleSep != theBuf[53]
				|| eleSep != theBuf[69] || eleSep != theBuf[76]) {
			throw new X12Exception("Invalid ISA element separators");
		}
		
		//Test CRLF scenarios
		if (theBuf[80] == 0x0D && theBuf[81] == 0x0A) {
			state = State.crlf80;
			//Shifts chars after the CRLF to complete the ISA
			for(int i = 80; i < (ANALYZE_BUFSIZE - 2); i++) {
				theBuf[i] = theBuf[i + 2];
			}
		} else if (theBuf[133] == 0x0D && theBuf[134] == 0x0A) {
			// Between 80 and the end should be all spaces
			boolean bAllSpaces = true;
			for (int i = 80; i < (ANALYZE_BUFSIZE - 2); i++) {
				if (theBuf[i] != 0x20) {
					bAllSpaces = false;
					break;
				}
			}

			if (bAllSpaces) {
				state = State.crlf133;
				//Read another 26 chars to complete the ISA
				if(reader.read(theBuf, 80, 26) != 26){
					throw new X12Exception("Invalid ISA 133");
				}
			}
		} else {
			state = State.noAction;
		}
		
		if(state == State.unknown){
			throw new X12Exception("Unknown X12 data");
		}
		
		//Check remaining element separators 
		if (eleSep != theBuf[81]
				|| eleSep != theBuf[83] || eleSep != theBuf[89]
				|| eleSep != theBuf[99] || eleSep != theBuf[101]
				|| eleSep != theBuf[103]) {
			throw new X12Exception("Invalid ISA");
		}

		reader.close();
		
		return (state != State.noAction);
	}

	public void clean(InputStream input, OutputStream output) throws X12Exception, IOException {
		//Create the writer
		Writer theWriter = new BufferedWriter(new OutputStreamWriter(output));
		
		// Create the X12Reader
		X12Reader theReader = new X12Reader(input);

		// Read & Write ISA record
		ISASegment theISA = theReader.readISA();
		theWriter.write(theISA.toString());
		
		// Read & Write segments
		X12Segment seg = theReader.readSegment();
		while(seg != null){
			theWriter.write(seg.toString());
			seg = theReader.readSegment();
		}
		
		theWriter.flush();
	}
}
