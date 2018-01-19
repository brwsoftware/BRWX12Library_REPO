## BRWX12Library

A collection of classes designed to work with X12 data.

#### Classes

- X12Reader
- ISASegment
- X12Schema
- X12Identifier
- X12ConverterText
- X12ConverterXml

##### X12Reader Example

```java
X12Reader theReader = new X12Reader(inputStream);
//Always start by reading the ISA record
ISASegment isa = theReader.readISA();
X12Segment seg = theReader.readSegment();
while(seg != null) {
   seg = theReader.readSegment();
}
```

##### X12ConverterText Example

```java
//X12ConverterText writes each segment on a new line
X12ConverterText converter = new X12ConverterText();
converter.convert(inputStream, outputStream);
```

##### X12ConverterXml Example

```java
//Convert X12 data to a generic XML format
X12ConverterXml converter = new X12ConverterXml();
converter.convert(inputStream, outputStream);
```

```java
//Convert X12 data to a specific XML format based on X12Schema definition
X12Schema schema = new X12Schema();
schema.addTransactionSet(new FileInputStream(getSchemaPathName()));
X12ConverterXml converter = new X12ConverterXml();
converter.convert(inputStream, outputStream, schema);
```

##### X12Identifier Example
```java
X12Identifier.Attributes attrX12 = X12Identifier.getAttributes(new FileInputStream(theX12File));
String tsid = attrX12.getTransactionSet().getID();
String impl = attrX12.getTransactionSet().getImplementation();
```
