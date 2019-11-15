package cpdh;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javafx.concurrent.Task;

/**
 * This Class contains method that prepares and returns javafx.concurrent.Task objects.  
 * @author Aleksandar
 */
public class Tasks {

	/**
	 * prepares javafx Task that constructs Cpdh data sets with 50, 100 and 250 points.
	 * Each of individual sets is placed in a Map< Integer, CpdhDataSet>  
	 * MPEG7- shape image date set is assumed.
	 * @param dir directory where image date set is located
	 * @return configured javafx Task that constructs Cpdh data set
	 */
	static Task< Map<Integer, CpdhDataSet>> newConstructDataSetsTask(File dir) {
		
		return new Task< Map<Integer, CpdhDataSet> >() {			

			Map<Integer, CpdhDataSet> dataSets = new HashMap<>();
			
			@Override
			protected Map<Integer, CpdhDataSet> call() {
				
				updateProgress(1, 10000);
				
				updateMessage("Getting files");
				File[] files = dir.listFiles((directory, name) -> {
					return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".bmp");
				});
				if (files.length == 0) {
					updateMessage("Canceled. No picture files with supported extensions found.");
					cancel();
					return dataSets;
				}
				updateMessage("Constructing the data set ...");
				try {
					constructDataSetsFrom(files);					
				} catch (InterruptedException e) {
					dataSets.clear();
					updateMessage("Canceled.");
					cancel();
				}
				return dataSets;
			}

			private void constructDataSetsFrom(File[] files) throws InterruptedException {
				
				long progress = 0;
				
				CpdhDataSet dataSet50  = new CpdhDataSetImp(50);
				CpdhDataSet dataSet100 = new CpdhDataSetImp(100);
				CpdhDataSet dataSet250 = new CpdhDataSetImp(250);
				
				for (File file : files) {
					
					if (isCancelled() || Thread.interrupted()) {
						throw new InterruptedException(); 
					}
					try {
						dataSet50 .put(new Cpdh(file, 50, false));
						dataSet100.put(new Cpdh(file, 100, false));
						dataSet250.put(new Cpdh(file, 250, false));
						updateProgress(++progress, files.length);
					} catch (IllegalArgumentException | IndexOutOfBoundsException e) {
						updateMessage("Error caused by file: " + file.getName() + "\n error: \n" + e.getMessage());
						e.printStackTrace();
						cancel();
					}
				}					
				dataSets.put(50, dataSet50);
				dataSets.put(100, dataSet100);
				dataSets.put(250, dataSet250);
			}
		};
	}

	static Task<String> newMatchCpdhToGroupTask(Cpdh cpdh, CpdhDataSet dataSet) {
	
		return new Task<String>() {

			@Override
			protected String call() throws Exception {

				GroupScore bestGroup = dataSet.matchCpdhToGroup(cpdh);
				if (bestGroup.score < 0.0005)
					if (dataSet.containsCpdhInGroup(cpdh,bestGroup.groupName))
						updateMessage("Note: This image is most likely part of the data set.");
					else
						updateMessage("Note: This shape has identical CPDH as one in the group: " + bestGroup.groupName + ".");
				else {
					updateMessage("Shape retrieved.");
				}
				return bestGroup.groupName;
			}
		};
	}
}
