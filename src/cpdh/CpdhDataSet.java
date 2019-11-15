package cpdh;

import java.io.File;
import java.io.IOException;

public interface CpdhDataSet {
	
	boolean contains(Cpdh cpdh);
	
	/**
	 *
	 * @param cpdh that will be put in data set.
	 * @throws IllegalArgumentException when fails to put cpdh in data set.
	 */
	void put(Cpdh cpdh);
	
	/**
	 * 
	 * @param cpdh for which to find matching group-
	 * @return Name of the group that's best match for the input.
	 */
	GroupScore matchCpdhToGroup(Cpdh cpdh);
	
	/**
	 * 
	 * @param file to read from
	 * @throws IOException is thrown when there is a problem with the input file
	 */
	void loadFrom(File file) throws IOException;
	
	void writeTo(File file);

	boolean isEmpty();

	boolean containsCpdhInGroup(Cpdh cpdh, String groupName);

	int numOfCategories();

	int numOfCpdhs();
}
