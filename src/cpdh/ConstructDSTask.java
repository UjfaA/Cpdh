package cpdh;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javafx.concurrent.Task;

public class ConstructDSTask extends Task<List<List<Cpdh>> > {
	
	private final int groups = 70;
	private final int positions = 20;
	private final File[] files;
	private final int numOfPoints;
	
	public ConstructDSTask(File[] files, int numOfPoints) {

		this.files = files;
		this.numOfPoints = numOfPoints;
	}

	@Override
	protected List<List<Cpdh>> call() throws Exception {

		List<List<Cpdh>> dataSet = new ArrayList<List<Cpdh>>(groups); 
		
		for(int group = 0; group < groups; group++) {
			/* Load each group */
			List<Cpdh> cpdhGroup = new ArrayList<Cpdh>(positions);
			for(int position = 0; position < positions; position++) {
				int fileIndex = group * positions + position;
				cpdhGroup.add(new Cpdh(files[fileIndex], numOfPoints, false));
			}
			dataSet.add(cpdhGroup);
		}		
		System.out.println("Constructed data set " + numOfPoints);
		return dataSet;
	}
}