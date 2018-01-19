package com.brwsoftware.brwx12library;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

public class X12ConverterText {

	public X12ConverterText() {
	}
	
	public void convert(InputStream input, OutputStream output) throws IOException, X12Exception {
		convert(input, output, System.getProperty("line.separator"));
	}

	public void convert(InputStream input, OutputStream output, String seperator) throws IOException, X12Exception {
		//Create the writer
		Writer theWriter = new BufferedWriter(new OutputStreamWriter(output));
		
		// Create the X12Reader
		X12Reader theReader = new X12Reader(input);

		// Read & Write ISA record
		ISASegment theISA = theReader.readISA();
		theWriter.write(theISA.toString());
		theWriter.write(seperator);
		
		// Read & Write segments
		X12Segment seg = theReader.readSegment();
		while(seg != null){
			theWriter.write(seg.toString());
			theWriter.write(seperator);
			seg = theReader.readSegment();
		}
		
		theWriter.flush();
	}
}
