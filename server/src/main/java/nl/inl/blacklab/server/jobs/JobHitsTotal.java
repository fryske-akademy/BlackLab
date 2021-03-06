package nl.inl.blacklab.server.jobs;

import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.requesthandlers.SearchParameters;
import nl.inl.blacklab.server.search.SearchManager;

/**
 * Represents finding the total number of hits.
 */
public class JobHitsTotal extends Job {

	public static class JobDescHitsTotal extends JobDescription {

		public JobDescHitsTotal(SearchParameters param, JobDescription inputDesc, SearchSettings searchSettings) {
			super(param, JobHitsTotal.class, inputDesc, searchSettings);
		}

		@Override
		public String uniqueIdentifier() {
			return super.uniqueIdentifier() + ")";
		}

		@Override
		public String getUrlPath() {
			return "hits";
		}

	}

	private Hits hits = null;

	public JobHitsTotal(SearchManager searchMan, User user, JobDescription par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		// Get the total number of hits (we ignore the value because you can monitor progress
		// and get the final total through the getHits() method yourself.
		hits = ((JobWithHits)inputJob).getHits();
		setPriorityInternal(); // make sure hits has the right priority
		hits.size();
		if (Thread.interrupted()) {
			// We don't throw anymore because that will cause this error to re-throw even when we just
			// want to look at a page of results. maxHitsCounted is set to true, however, so the application
			// can detect that we stopped counting at some point.
			//throw new ServiceUnavailable("Determining total number of hits took too long, cancelled");
			if (threwException()) {
				logger.warn("Exception occurred while counting hits: " + getThrownException().getMessage());
			} else {
				if (!jobDesc.getSearchSettings().isUseCache())
					logger.warn("Search not cached, count aborted immediately.");
				else
					logger.warn("Determining total number of hits took too long, cancelled");
			}
		}
	}

	/**
	 * Returns the Hits object when available.
	 *
	 * @return the Hits object, or null if not available yet.
	 */
	public Hits getHits() {
		return hits;
	}

	@Override
	protected void dataStreamSubclassEntries(DataStream ds) {
		ds	.entry("hitsCounted", hits != null ? hits.countSoFarHitsCounted() : -1);
	}

	@Override
	protected void cleanup() {
		hits = null;
		super.cleanup();
	}

	@Override
	protected Hits getObjectToPrioritize() {
		return hits;
	}

}
