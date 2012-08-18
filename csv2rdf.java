/* Translate CSV to RDF in a very simple translation.
	Assumes the first line is the attributes
	Take as input which attribute is the ID of the instance 
	Take as input the type of the instance (which is not in the CSV)
	
	The CSV file must be complete (same number of items on each line)
	Does very very minimal error checking 
	
	compile:
	javac -classpath '.:opencsv-2.3.jar' csv2rdf.java
	
	make jar:
	jar cmf manifest.txt csv2rdf.jar *.class au

	run
	java -jar csv2rdf.jar <csv file name>
	
	
	changes:
	12.08.18 fix fixAttributeName to really strip the quotes.  Ooops
	12.07.27 redo to use configuration file to help with translation
				step 1: implement data structures for keeping track of classes and properties
						  with current interface.
				step 2: read and process configuration file.
	12.06.27 change spaces to underscores in attribute names.
				trim leading/trailing spaces from data.
	
*/
	
import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;


public class csv2rdf {
	private static final String INPUT_FILE="input.csv";
   public static final Boolean DEBUG = false;

	static int blankCounter;	// keep track of blanks seen in config file and then in header file.
	public static String BaseFileName(String name) {
		// takes a file name (like input.csv) and returns it without the extension
		int p = name.lastIndexOf(".");
		
		if (p == -1) {
			return name;
		} else {
			return name.substring(0,p);
		}
	}
	
	public static CSVConfig readConfigFile(String configFile) throws IOException {
		CSVConfig config = new CSVConfig();
		
		CSVReader configReader;
		configReader = new CSVReader(new FileReader(configFile));	// if there's a config file this will open it.
		
		System.out.println("Reading Configuration File: " + configFile);
		
		String [] nextLine;
		while ((nextLine = configReader.readNext()) != null) {
			// process one line at a time.
			// of the form csv_name type(rdf_name)	class_name
			// type can be "class" or "property"
			// class_name is the name of the rdf class to use or the superclass
			// if any of these fields is blank, at least the tab must be there.
			// minimal error checking
			
			if (nextLine.length == 3) {
				String csv_name = renameBlankAttribute(fixAttributeName(nextLine[0]));	// fixes this name like the header (trim, underscores for blanks, unlabeled1, etc)
				
				String [] typeInfo = nextLine[1].split("[\\(\\)]");		// split out what's inside the parenthesis
				
				String itemType = typeInfo[0].trim().toLowerCase();
				
				String rdf_name = "";
				if (typeInfo.length > 1) {
					rdf_name = fixAttributeName(typeInfo[1]);	// may still be blank after this.
				}
				if (rdf_name.length() == 0) rdf_name = csv_name;  // there's not a different rdf_name for this item
				
				String class_name = nextLine[2].trim();
				if (class_name.length() > 1) class_name = fixAttributeName(class_name);   // in case there are quotes, etc
				// OK now add this. Config object will look at the type and do the right thing.
				config.addItem(itemType, csv_name, rdf_name, class_name);

				
			} else {
				System.out.println("Badly formed instance, skipping");
				System.out.print("   <");
				for (int i=0;i<nextLine.length; i++) {
					System.out.print(nextLine[i] + " ");
				}
				System.out.println(">");
			}
		}
		configReader.close();	
		
		return config;
	}
	
	public static CSVConfig doManualConfig(String fileName) {
		CSVConfig config = null;
		try {

			CSVReader reader;
			reader = new CSVReader(new FileReader(fileName));	// if no file, will be caught by outer call
			
			String [] attributes = reader.readNext(); // header line
			reader.close();	// done with the file for our test
			
			config = new CSVConfig(attributes);	// create a config file from the line with asking user questions.
			
			String configName = configFileName(fileName);
			System.out.println("Writing config file to " + configName);
			config.writeToFile(configName);
		}
		catch (IOException i) {
			// no file.  will catch it later.
			config = new CSVConfig();
		}
		return config;
	}

	public static String fixAttributeName(String att) {
		// trim, replace spaces with underscores, remove quotes, etc.
		// used to sanitize attributes read from the csv or the config file
		// to make them work for the rdf file.	
		att = att.trim();
		att = att.replaceAll(" ","_");
		att = att.replaceAll("\"", "");	// remove any quotes, too;
		att = att.replaceAll("/", "-");
		return att;
	}
	
	public static String renameBlankAttribute(String att) {
		// uses the global blankCounter to keep track of how many blanks we've seen.
		// and change empty strings to "unlabeled1", "unlabeled2" ...
		// this is used only to sanitize the csv header data and the csv name in the config
	
		if (att.length() == 0) {
			blankCounter++;
			att = "unlabeled" + blankCounter;
		} 
		return att;
	}
	public static void fixAttributes(String[] attributes) {
		// replace any spaces in the attributes with underscores
		// replace any blank attributes with "unlabeled1", "unlabeled2" -- RH 12.07.03
		// (now that happens in fixAttributeName)
		for (int i = 0; i < attributes.length; i++) {
			attributes[i] = renameBlankAttribute(fixAttributeName(attributes[i]));
		}
	}
	
	public static boolean containsData(String s) {
		// returns false if s is an empty String
		// or the word "null" (or NULL)
		return ((s.length() > 0)  && !s.toLowerCase().equals("null"));

	}
	
	
	public static void fixInputLine(String[] data) {
		for (int i = 0; i < data.length; i++) {
			data[i] = data[i].trim();	// fix up the data 
		}
	}
	
	public static String configFileName(String fileName) {
	
		return BaseFileName(fileName) + "-config.csv";

	}

   
	public static void main(String[] args) throws IOException {
		
		
		String fileName, outputFile;
		
		if (args.length > 0) {
			fileName = args[0];
		} else {
			fileName = INPUT_FILE;	// default if none is specified.
		}
		
		
		String baseFileName = BaseFileName(fileName);
		CSVConfig config;	
		
		blankCounter = 0;	// before we read the file, reset the blank counter
		// is there a configuration file?
	  	try {  
			config = readConfigFile(configFileName(fileName));
		}		
	   catch (IOException ioe) {
			// no configuration file, just ask a few questions and do simple configuration from that.
			// use info from the header of the main file.
			System.out.println("No configuration file, will construct one from " + fileName);
			config = doManualConfig(fileName);
			if (DEBUG) {
				System.out.println("CONFIG file: ");
				config.writeToFile("");
				System.out.println();
			}
	  }

		// we have now got our configuration.  Print it out if we're debugging.
			if (config.numClasses() > 0)	System.out.println("\nClasses and Properties");
		
		   for (String cName : config.classes()) {
		   	System.out.print(cName);
		   	if (config.superclassOf(cName) != null) System.out.print(" (" + config.superclassOf(cName) + ")");
		   	System.out.print(": ");
		   	for (String pName : config.getProperties(cName)) {
		   		System.out.print(pName + " ");
		   	}
		   	System.out.println("");
		   }
		   System.out.println("");
		   
		   

		 		   	
	
		// Now read in the input file and process it using the information we stored from the configuration step.
	  	try {  // handle error where file doesn't exist.
			CSVReader reader;
			reader = new CSVReader(new FileReader(fileName));
			System.out.println("Reading CSV from " + fileName);
			
		   blankCounter = 0;	// before we read the file, reset the blank counter
			String [] attributes = reader.readNext(); // header line
			
			if (attributes != null) {
				int numAttributes = attributes.length;
				
				fixAttributes(attributes);	 //  consistent with the config file... 
				
				// go through header line and put the column numbers into the config file.
				for (int i = 0; i < numAttributes; i++) {
					// is is the column
					config.setItemColumn(attributes[i], i);
				}
				
				if (DEBUG) {
					System.out.println("ITEMS");
				
					for (String iName : config.csvItems()) {
						int column = config.getItemColumn(iName);
						System.out.println(iName + " " + column);
					}
						
					System.out.println("");
				}

				outputFile = baseFileName + ".rdf";
				System.out.println("Writing RDF to " + outputFile);
				RDFWriter writer = new RDFWriter(outputFile);		
				writer.startRDF();	// this writes header and beginning part of the file.
				// write out the classes and property descriptions
				for (String className : config.classes()) {
					// write the class description
					CSVConfig.HeaderClass c = config.getClass(className);	// definitely there.
					writer.writeClassInfo(className, c.superClassName());
					// write the property descriptions
					for (String propName : config.getProperties(className)) {
						writer.writePropertyTag(propName, className);
					}
				}
				
				// now write the individual instances
				String [] nextLine;
				while ((nextLine = reader.readNext()) != null) {
					// process one line at a time.
					// assuming well formed input - length of each line is exactly the same as length of header.
					// add some error checking
					
					if (nextLine.length == numAttributes) {
						fixInputLine(nextLine);	// trims each string
						// drive the writing of the data from the classes/properties in config
						for (String className : config.classes()) {
							CSVConfig.HeaderItem item = config.getClass(className);
							int col = item.column();
							if ((col != -1) && containsData(nextLine[col]))	{
								// if there is no data for the class field, don't write this line at all for that class
								// (or this could be unlabeledx...?
								
								 // (need to sanitize this data (underscores, etc) because it's an ID)
								writer.startInstance(fixAttributeName(nextLine[col]), className);
								if (DEBUG) System.out.println("Instance of " + className + " " +'"' + nextLine[col] + '"');
								for (String propName : config.getProperties(className)) {
									// write a line for each property that has data
								 	CSVConfig.HeaderItem propItem = config.getProperty(className, propName);
								 	if (propItem != null) {
								 		int propCol = propItem.column();
								 		if ((propCol != -1) && containsData(nextLine[propCol])) {
								 			// we have a column for this property, and that column has data in this instance
								 			// don't sanitize this data because it's not in a header. (?)
								 			writer.writeAttributeData(propName, nextLine[propCol]);
									    	if (DEBUG) System.out.println("	 " + propName  + " " + '"' + nextLine[propCol] + '"');
								 		}
								 	}
								}
								writer.endInstance();
							}	
						}
						// done with this line of the file.
						if (DEBUG) System.out.println("---");

		
					} else {
						System.out.println("Badly formed instance, skipping");
						System.out.print("   <");
						for (int i=0;i<nextLine.length; i++) {
							System.out.print(nextLine[i] + " ");
						}
						System.out.println(">");
					}
				}
				writer.endRDF();	// anything that goes at the end of the RDF (and closes the file)
	
				reader.close(); 
	
			} else {
				System.out.println("Empty input file:" + fileName);
			}
      }
	  catch (IOException ioe) {
			System.err.println("Could not open file:" + fileName);
	  }

		

	}
	

}