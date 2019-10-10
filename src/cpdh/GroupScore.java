package cpdh;

public class GroupScore implements Comparable<GroupScore>{
	
	final String groupName;
	final double score;
	
	GroupScore( String groupName, double score) {
		this.groupName = groupName;
		this.score = score;
	}
	
	@Override
	public int compareTo(GroupScore other) {
		return Double.compare(this.score, other.score);
	}
}
