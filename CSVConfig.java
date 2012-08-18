/* 
	class csvconfig
	can either be initialized with a config file where each line in the config file 
	corresponds to a column in the csv file header and describes the mapping
	OR can be initialized with an array of attributes (from the first line of the CSV)
	and the translation can be inferred from questions to the user (like we did it before)
	
	CSVConfig stores classes and properties
	and can relate them to which column they represent in a CSV file
	
	12.08.18 keep the classes in order so they are outputed superclasses before subclasses
	         keep as separate list for use when iterating (because mostly need to look up by name)
	         
	
*/
	
import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;


public class CSVConfig {
	private Map<String, HeaderClass> classes;	// list of the classes we found and the properties within them
	private ArrayList<String> classOrder;  // ordered list with superclasses before subclasses.
	
	private Map<String, List<HeaderItem>> csvItems;	// list of all the classes and properties indexed by csv name
																   // maps the csv name to a list of items for each name.
																   // (one column can be both a property and a class)
	 
	public enum RDFType { CLASS, PROPERTY };
   public static final Boolean DEBUG = false;
	
	static class HeaderItem {
		String csv_name;	// the name of the column in the header of the csv file
		int csv_column;	// which column this item represents in the CSV file (-1 if none or not set yet)	
		String rdf_name;	// the name to use in the rdf fileName

	   public HeaderItem(String csv_name, String rdf_name, int csv_column) {
	   	this.csv_name = csv_name;
	   	this.csv_column = csv_column;
	   	this. rdf_name = rdf_name;
	   }
	   
	   public void set_column(int column) { csv_column = column; }
		public void set_rdf_name(String name) { rdf_name = name; }
		public void set_csv_name(String name) { csv_name = name; }

	   public int column() { return csv_column; }
		public String csv_name() { return csv_name; }
		public String rdf_name() { return rdf_name; }
		
		public String itemType() { return "Item"; }	// will be overridden by subclasses
		public String className() {return ""; }	// will be overridden
	}
	
	static class HeaderProperty extends HeaderItem {
		String class_name;	
	
		public HeaderProperty(String csv_name, String rdf_name, String class_name) {
			super(csv_name, rdf_name, -1);
			this.class_name = class_name;	//doesn't check if class exists, leaves that to caller
		}
		
		public String itemType() { return "property"; }	
		public String className() {return class_name; }	// will be overridden
	}
	
	static class HeaderClass extends HeaderItem {
		String superClassName;
		Map<String,HeaderProperty> properties;	

		public HeaderClass(String csv_name, String rdf_name, String superClassName) {
			// won't know the properties or the csv_column when creating
			super(csv_name, rdf_name, -1);
			this.superClassName = superClassName;
			properties = new HashMap<String, HeaderProperty>();	// empty list of properties
		}
		
		public String itemType() { return "class"; }	
		public String className() {return superClassName; }	
	
		public void addProperty(HeaderProperty property) {
			// what do we do if it exists already?
			properties.put(property.rdf_name, property);
			
		}
		
		public int numProperties() {
			// how many properties are associated with this class
			return properties.size();
		}
		
		public Set<String> properties() {
			return properties.keySet();	// list of the names of the properties
		}
		
		public String superClassName() { return superClassName; }
		public void setSuperClassName(String name) {  superClassName = name; }
	}
	
	
	public CSVConfig () {
		// no parameters, just initialize an empty configuration structure
		classes = new HashMap<String, HeaderClass>();   // indexed by name
		classOrder = new ArrayList<String>();       // sorted by hierarchy
		csvItems = new HashMap<String, List<HeaderItem>>();
	}
	
	public CSVConfig(String[] attributes) {
		// initialize without a config file (from just a list of the header items)
		// ask the user for help like before.
		this();	
		
		if (attributes != null) {
			int idAttribute = AskForIDAttribute(attributes);	// find out which attribute is the one to be used as ID
			String s = attributes[idAttribute].trim();
			System.out.println("(Chose " + '"' + s + '"' + ')');
			String className = AskForInstanceType();
			if (className.length() == 0) className = s;	// no different class name
			System.out.println("(Chose " + '"' + className + '"' + ')');
			String superclassName = AskForSuperClassName();
			System.out.println("(Using superclass " + '"' + superclassName + '"' + ')');
			
			// first create the superclass they just picked.
			addClass("",superclassName,"");
			
			// now create the class (id attribute)
			addClass(attributes[idAttribute], className, superclassName);
			
			// now add the rest of the lines as properties of that class.
			for (int i = 0; i < attributes.length; i++) {
				if (i != idAttribute) {
					addProperty(attributes[i], attributes[i], className);
				}
			}
			
		}
	}
	
	public String[] classes() { 
		return  classOrder.toArray(new String[classOrder.size()]); 	// the names of the classes in hierarchy order
	}
	
	public Set<String> csvItems() {
		return csvItems.keySet();	// names of all the items
	}
	
	
	public Set<String> getProperties(String className) {
		HeaderClass c = getClass(className);
		if (c != null) {
			return c.properties();
		} else return null;
	}
	
	public int numClasses() {
		return classes.size();
	}
	
	public String superclassOf(String className) {
		HeaderClass c = getClass(className);
		if (c != null) {
			return c.superClassName();
		} else return null;
	   
	}
	
	private void addCSVItem(HeaderItem item) {
		// used internally when we already have an existing item to add.
		// adds the item to the list, indexed by csv_name 
		// if csv_name is empty, just don't add it to the list - it's a placeholder item
		
		if (item.csv_name.length() > 0) {
			List<HeaderItem> list = csvItems.get(item.csv_name);
			if (list == null) {
				// nothing there yet, make an empty list
				list = new ArrayList<HeaderItem>();
			}
			list.add(item);	// add to the list
			csvItems.put(item.csv_name, list);
		} 
		
	}
		
	
	public HeaderClass addClass(String csv_name, String rdf_name, String superclass_name) {
		// checks if a class indexed by rdf_name exists and adds it if it doesn't
		// also updates fields which have changed and adds superclass if it doesn't exist
		
		// also adds it to the list of items using addItem;
		
		// NOTE: does NOT look up by csv_name.  The only scenario we have where a class will be added
		// as a placeholder comes from the translation document, where a class is referenced before 
		// it is defined.  This should ALWAYS be the rdf_name for the class.
		// I thought about putting in a case where we know the csv_name first, but decided
		// we might have a legit case where the csv_name for one thing is the rdf_name for another
		// so this wouldn't work.

		if (DEBUG) System.out.println("	Adding class:     " + csv_name + "(" + rdf_name + ")");
		HeaderClass c = getClass(rdf_name);	

		if (c == null) {
			c = new HeaderClass(csv_name, rdf_name, superclass_name);
			addToClassList(rdf_name, c);
			
		} else {
			// if it already exists, we might be updating after we put in placeholders before

			if (c.superClassName() != superclass_name) {
				c.setSuperClassName(superclass_name);
				// remove it and add it again because this can change the sorting
            removeFromClassList(rdf_name);
            addToClassList(rdf_name, c);
		   }
			if (c.csv_name() != csv_name) {
				c.set_csv_name(csv_name);	
			}
		}
		
		// add it to the list of items if it's not already there
		addCSVItem(c);
		
		// in all cases, we want to create the superclass if it doesn't exist.
		if ((superclass_name.length() > 0) && (!classExists(superclass_name))) {
			addClass("", superclass_name, "");		// put in a placeholder
		}
		
		return c;
	}
	
	public HeaderClass getClass(String name) {
			return classes.get(name);	
	}
	
	private HeaderItem getCSVItem(String name) {
		HeaderItem item = null;
		List<HeaderItem> list = csvItems.get(name);
		if (list != null) {
			item = list.get(0);
		}
		return item;
	}
	
	public HeaderProperty getProperty(String className, String propName) {
		HeaderClass c = getClass(className);
		HeaderProperty prop = null;
		if (c != null) prop = c.properties.get(propName);
		return prop;	// will be null if the class or the property don't exist.
	}

   private void addToClassList(String rdf_name, HeaderClass newClass) {
      // put this item onto our list of classes
      // - add to hashmap
      // - and add to sorted list of class names by hierarchy
      // this way we can look up quickly by name, or go through list by hierarchy
      // We have already determined that this needs to be added to the list.
      
      classes.put(rdf_name, newClass);
      
      int target = -1;   // location to put this class
      for (int i = 0; i < classOrder.size(); i++) {   // go through classes and put this one in the right place
         
         HeaderClass c = getClass(classOrder.get(i));  
         if (c.superClassName().equals(rdf_name)) {  
            if (DEBUG) System.out.println("Found subclass " + c.className() + " of " + rdf_name);
            target = i;
            break;   // found where to put it
         }
         
      }
      if (target == -1) target = classOrder.size();   // never found a subclass, just put us at the end.
      
      classOrder.add(target, rdf_name);
      
   }

	private void removeFromClassList(String name) {
		classes.remove(name);
		classOrder.remove(name);
	}
	
	
	public void addProperty(String csv_name, String rdf_name, String class_name) {
		 if (DEBUG) System.out.println("	Adding property:  " + csv_name + " (" + rdf_name + ")");
		 HeaderProperty p = new HeaderProperty(csv_name, rdf_name, class_name);
		 HeaderClass c = getClass(class_name);
		if (c == null)  {
			c = addClass("", class_name, "");	// add placeholder
		}
		c.addProperty(p);
		addCSVItem(p);	
		
	}
	
	public void addItem(String itemType, String csv_name, String rdf_name, String class_name) {
		// add a Header item of the right type
		// for now, if the string is exactly "class" make a class
		// if it starts with "prop" make a property
		// otherwise just ignore it 
		if (DEBUG) System.out.println("Adding item: " + itemType + " " + csv_name + " " + rdf_name + " " + class_name);

		if (itemType.equals("class")) addClass(csv_name, rdf_name, class_name);
		else if (itemType.startsWith("prop")) addProperty(csv_name, rdf_name, class_name);
		else 	{
		// otherwise it says ignore so ignore it.
			if (DEBUG) System.out.println("(ignoring)");
		}

	}
	
	public boolean classExists(String name) {
		// simple case assume name is the rdf_name.  Could also check if it's the csv name for any CLASS
		return classes.containsKey(name);
	}
	
	public void setItemColumn(String csv_name, int column) {
		// look up an item by it's name in the csv file and set it's column number
		// There may be more than one which matches this name (e.g. a property and a class)
		List<HeaderItem> list = csvItems.get(csv_name);
		if (list != null) {
			for (HeaderItem item : list) {
				item.set_column(column);
			}
		}

		// if it's not there that means the column doesn't exist in the config file.
		// for now that means we are going to ignore it.
		
	}

	public int getItemColumn(String csv_name) {
		// look up an item by it's name in the csv file and get it's column number
		// this should still work because by definition the column name is the same in all the items
		int column = -1;
		HeaderItem item = getCSVItem(csv_name);
		if (item != null) {
			column = item.column();
		} 
		return column;
	}
	
	public static int AskForIDAttribute(String[] attributes) {
		// given an array of strings, ask the user which one to use as the id field.
		// but for now, just automatically return the index of the attribute "name"
		// I know that will be the second element for our test file so to start just return 1

		for (int i = 0; i < attributes.length; i++) {
				System.out.printf("%d: %s\n", i, attributes[i]);
		
		}

		int num = -1;
		while(true)
		  {  
			// loop until they enter a valid number
		  		num = Console.readInt("Which attribute will be used as the class?");
				if (num >= 0 && num < attributes.length) {
					break;
				} else {
					Console.printPrompt("Not a valid entry, please enter a number between 0 and " + (attributes.length - 1) + "\n");
				}	 
		  }


		return num;
	
	}

	public static String AskForInstanceType() {
		// ask the user for a string to use as the type of the instance.
		// if it's blank, return a blank.
		String instanceType = "";
		
		String response;
		  
		response = Console.readLine("Please enter a string to use for the name of the class:").trim();
		if (response.length() > 0) {
			// trim off quotes if they put them there.
			instanceType = response.replaceAll("^\"|\"$", "").trim().replaceAll(" ","_");
		} 
		
		return instanceType;
	}

	public static String AskForSuperClassName() {
		// ask the user if they want an "all" class (instead of just Thing.
		// an answer starting with y gets an all class. Any other answer doesn't

		String superclass;
		
		String response;
		  
		response = Console.readLine("Do you want an \"all\" class (y or n)?");
		response = response.trim();
		char firstChar = (response.length() == 0) ? 'n' : response.charAt(0);
		if (firstChar == 'y' || firstChar == 'Y') {
			superclass = "all";
		} else {
			superclass = "Thing";
		}
		
		return superclass;
	}

	public void writeToFile(String fileName) {
		// write out the configuration to a file 
		// do this based on the csvitems list 
		Out out;
		if (fileName.length() == 0 ) out = new Out();
		else out = new Out(fileName);
		for (List<HeaderItem> list : csvItems.values()) {
			// list associated with each entry in csvItems
			for (HeaderItem h : list) {
				out.print(h.csv_name + ",");
				out.print(h.itemType());
				if (!h.rdf_name.equals(h.csv_name)) out.print("(" + h.rdf_name + ")");
				out.println("," + h.className());
			}
			
		}
	}
	

   private static final String INPUT_FILE="input.csv";

	
	public static void main(String[] args) throws IOException {
	

		String fileName, outputFile;
		
		if (args.length > 0) {
			fileName = args[0];
		} else {
			fileName = INPUT_FILE;	// default if none is specified.
		}
		
		
	  	try {  // handle error where file doesn't exist.
			CSVReader reader;
			reader = new CSVReader(new FileReader(fileName));
			System.out.println("Reading CSV from " + fileName);
			String [] attributes = reader.readNext(); // header line
		   reader.close();	// done with the file for our test
		   
		   CSVConfig config = new CSVConfig(attributes);	// create a config file from the line with asking user questions.
		   
		   // write out what we did.
		   System.out.println("Classes");
		   for (String cName : config.classes()) {
		   	System.out.print(cName + ": ");
		   	HeaderClass c = config.getClass(cName);
		   	for (String pName : c.properties()) {
		   		System.out.print(pName + " ");
		   	}
		   	System.out.println("");
		   }
      }
	  catch (IOException ioe) {
			System.err.println("Could not open file:" + fileName);
	  }
	}
	

}