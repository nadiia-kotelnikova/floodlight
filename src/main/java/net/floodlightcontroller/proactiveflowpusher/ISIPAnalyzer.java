package net.floodlightcontroller.proactiveflowpusher;

import java.util.ArrayList;
import java.util.HashMap;

import net.floodlightcontroller.core.module.IFloodlightService;

public interface ISIPAnalyzer extends IFloodlightService {
	public HashMap<String, ArrayList<String>> getExtractedDataTable();
	public HashMap<String, ArrayList<String>> getPathIDTable();
}
