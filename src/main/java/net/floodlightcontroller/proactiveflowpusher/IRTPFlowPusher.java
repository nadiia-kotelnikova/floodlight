package net.floodlightcontroller.proactiveflowpusher;

import java.util.ArrayList;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.routing.Path;

public interface IRTPFlowPusher extends IFloodlightService {
	public void flowPucher(ArrayList<String> parameters, Path path);
}
