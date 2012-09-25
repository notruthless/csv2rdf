/* Write to an RDF file
	
   Ruth Helfinstein
   July 2012
   	
	changes:
   12.09.24 add a "#" in writeAttributeDataResource!
   12.09.18 when writing properties, allow different "ranges" (types) besides String.
            when writing attribute data, write other types besides string (references to properties)	
	12.08.18 if a property/class has no superclass listed, make it a subclass of Thing
	         (tried to do this before, but was checking for empty string incorrectly, oops)
*/
	
public class RDFWriter {
// for now this just writes very simple RDF to the file specified, or to the stdout
// but I'm doing it as a class to abstract it away from reading from the csv

	// instance variables
	private  Out out;
	public final String STRING_RANGE = "http://www.w3.org/2001/XMLSchema#string";
	
	// constructor
	public RDFWriter() {
		// no params = write to stdout
		out = new Out();
		
	
	}
	
	public RDFWriter(String s) {
	// file name
		out = new Out(s);
	}
	

	public void endRDF() {
		// write whatever goes at the botom of the rdf file
		// and close the file.
		out.println("</rdf:RDF>");
		out.close();
	}
	
	public void writePropertyTag(String attributeName, String className, String rangeType) {
	   // allow for rangeTypes other than "String".  If rangeType is empty, use "#string"
	   // have to put a "#" before a primitive type but not in front of a "schema" type.
	   // grr.  Maybe just check if the string has a # and add one to the beginning if not.
	   
		out.printf( "<rdf:Property rdf:ID=\"%s\">\n", attributeName);
		out.printf( "  <rdfs:domain rdf:resource=\"#%s\"/>\n", className);  
		if (rangeType.length() == 0) rangeType = STRING_RANGE;
		else if (!rangeType.contains("#")) rangeType = "#" + rangeType;
		
		out.printf("  <rdfs:range rdf:resource=\"%s\"/>\n", rangeType); 
		out.println("</rdf:Property>");
		out.println(" ");
	}
	
	
	public void writeClassInfo(String className, String superclass) {
		out.printf("<rdf:Description rdf:ID=\"%s\">\n", className);   
		out.println("  <rdf:type rdf:resource=\"http://www.w3.org/2000/01/rdf-schema#Class\"/>");
		if (superclass.length() == 0) superclass = "Thing";
		out.printf("  <rdfs:subClassOf rdf:resource=\"#%s\"/>", superclass);
		out.println("\n</rdf:Description>");
		out.println(" ");
	}

	public void startInstance(String id, String className) {
		// start a new instance, writes the opening rdf:description tag, and the rdf:type tag
		out.printf("<rdf:Description rdf:ID=\"%s\">\n", id);
		out.printf("  <rdf:type rdf:resource=\"%s\"/>\n", className);
	}

	public void endInstance() {
		out.printf("</rdf:Description>\n");
		out.println(" ");
}
	
	
	public void writeAttributeData(String attName, String attData) {
	   // <sample:price>14.00</sample:price>
		out.printf("  <sample:%s>%s</sample:%s>\n", attName, attData, attName);
	}

	public void writeAttributeDataResource(String attName, String attData) {
	   // <sample:fromcountry rdf:resource="Thailand" />
		out.printf("  <sample:%s rdf:resource=\"#%s\"/>\n", attName, attData);
	}
	
	
	
	public void startRDF() {
		// write whatever goes at the top of the rdf file
				
		// open the rdf tag, and write header info
		out.println("<rdf:RDF");
  		out.println("  xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"");
  		out.println("  xmlns:rdfs=\"http://www.w3.org/2000/01/rdf-schema#\"");
  		out.println("  xmlns:owl=\"http://www.w3.org/2002/07/owl#\"");
  		out.println("  xmlns:sample=\"http://www.test.nql.org/schemas/sample#\">");
  		out.println("<owl:Ontology rdf:about=\"http://www.w3.org/2000/01/rdf-schema#\"/>");
		out.println(" ");
	
		// classes and property descriptions will be written by the caller
	}


}