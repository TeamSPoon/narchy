package mcaixictw;

/**
 * Used to restore a previous state of the agent.
 */
public class ModelUndo {

	public ModelUndo(Agent agent) {
		age = agent.age();
		reward = agent.reward();
		historySize = agent.historySize();
		lastUpdatePercept = agent.lastUpdatePercept();
	}

	private int age;
	private int reward;
	private int historySize;
	private boolean lastUpdatePercept;

	public int age() {
		return age;
	}

	public int reward() {
		return reward;
	}

	public int getHistorySize() {
		return historySize;
	}

	public boolean isLastUpdatePercept() {
		return lastUpdatePercept;
	}
}
