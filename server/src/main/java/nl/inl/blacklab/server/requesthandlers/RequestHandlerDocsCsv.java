package nl.inl.blacklab.server.requesthandlers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.document.Document;

import nl.inl.blacklab.perdocument.DocGroup;
import nl.inl.blacklab.perdocument.DocGroups;
import nl.inl.blacklab.perdocument.DocProperty;
import nl.inl.blacklab.perdocument.DocResult;
import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.search.grouping.HitPropValue;
import nl.inl.blacklab.search.indexstructure.IndexStructure;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.datastream.DataStreamPlain;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.jobs.JobDocsGrouped;
import nl.inl.blacklab.server.jobs.JobWithDocs;
import nl.inl.blacklab.server.jobs.User;

/**
 * Request handler for hit results.
 */
public class RequestHandlerDocsCsv extends RequestHandler {
	public RequestHandlerDocsCsv(BlackLabServer servlet, HttpServletRequest request, User user, String indexName, String urlResource, String urlPathPart) {
		super(servlet, request, user, indexName, urlResource, urlPathPart);
	}

	/**
	 * Get the docs (and the groups from which they were extracted - if applicable) or the groups for this request.
	 * Exceptions cleanly mapping to http error responses are thrown if any part of the request cannot be fulfilled.
	 * Sorting is already applied to the results.
	 *
	 * @return Docs if looking at ungrouped results, Docs+Groups if looking at results within a group, Groups if looking at groups but not within a specific group.
	 * @throws BlsException
	 */
	// TODO share with regular RequestHandlerHits, allow configuring windows, totals, etc ?
	private Pair<DocResults, DocGroups> getDocs() throws BlsException {
		// Might be null
		String groupBy = searchParam.getString("group"); if (groupBy.isEmpty()) groupBy = null;
		String viewGroup = searchParam.getString("viewgroup"); if (viewGroup.isEmpty()) viewGroup = null;
		String sortBy = searchParam.getString("sort"); if (sortBy.isEmpty()) sortBy = null;

		DocResults docs = null;
		DocGroups groups = null;

		if (groupBy != null) {
			JobDocsGrouped searchGrouped = (JobDocsGrouped) searchMan.search(user, searchParam.docsGrouped(), true);
			groups = searchGrouped.getGroups();
			
			if (viewGroup != null) {
				// TODO handle group not present in result & cannot deserialize group parameter error
				DocGroup group = groups.getGroup(HitPropValue.deserialize(groups.getOriginalDocResults().getOriginalHits(), viewGroup));
				docs = group.getResults();
			
				// TODO sortBy is automatically applied to regular hits and groups
				// TODO test regular group view ordering though
				// but is applied to Group ordering instead of hit ordering within group with we have both group and viewGroup
				// need to fix this in SearchParameters somewhere
				if (sortBy != null) {
					// TODO handle cannot deserialize, invalid prop etc
					DocProperty sortProp = DocProperty.deserialize(sortBy);
					docs.sort(sortProp, sortProp.isReverse());
				}
			}
		} else  {
			// Don't use JobDocsAll, as we only might not need them all.
			JobWithDocs job = (JobWithDocs) searchMan.search(user, searchParam.docsSorted(), true);
			docs = job.getDocResults();
		}

		// apply window settings
		// Different from the regular results, if no parameters are provided, all results are returned.
		if (docs != null && (searchParam.containsKey("first") || searchParam.containsKey("number"))) { 
			int first = Math.max(0, searchParam.getInteger("first")); // Defaults to 0
			int number = searchParam.containsKey("number") ? Math.max(1, searchParam.getInteger("number")) : Integer.MAX_VALUE;
			
			if (!docs.sizeAtLeast(first)) 
				first = 0;
			docs = docs.window(first, number);
		}

		return Pair.of(docs, groups);
	}
	
	private static void writeGroups(DocGroups groups, DataStreamPlain ds) throws BlsException {
		try {
			// Write the header
			List<String> row = new ArrayList<>();
			row.addAll(groups.getGroupCriteria().getPropNames());
			row.add("count");
	
			CSVPrinter printer = CSVFormat.EXCEL.withHeader(row.toArray(new String[0])).print(new StringBuilder("sep=,\r\n"));
	
			// write the groups
			for (DocGroup group : groups) {
				row.clear();
				row.addAll(group.getIdentity().getPropValues());
				row.add(Integer.toString(group.getResults().countSoFarDocsCounted()));
				printer.printRecord(row);
			}
	
			printer.flush();
			ds.plain(printer.getOut().toString());
		} catch (IOException e) {
			// TODO proper message
			throw new InternalServerError("Cannot write response", 1337);
		}
	}

	private void writeDocs(DocResults docs, DataStreamPlain ds) throws BlsException {
		try {
			IndexStructure struct = this.getSearcher().getIndexStructure();
			String pidField = struct.pidField();
			String tokenLengthField = struct.getMainContentsField().getTokenLengthField();
	
			// Build the header; 2 columns for pid and length, then 1 for each metadata field
			List<String> row = new ArrayList<>();
			row.add("docPid");
			row.add("numberOfHits");
			if (tokenLengthField != null)
				row.add("lengthInTokens");
			
			Collection<String> metadataFieldIds = struct.getMetadataFields();
			metadataFieldIds.remove("docPid"); // never show these values even if they exist as actual fields, they're internal/calculated
			metadataFieldIds.remove("lengthInTokens"); 
			metadataFieldIds.remove("mayView");
			
			for (String fieldId : metadataFieldIds) {
				row.add(struct.getMetadataFieldDesc(fieldId).getDisplayName());
			}
			
			// Explicitly declare the separator, excel normally uses a locale-dependent CSV-separator...
			CSVPrinter printer = CSVFormat.EXCEL.withHeader(row.toArray(new String[0])).print(new StringBuilder("sep=,\r\n"));
	
			int subtractFromLength = struct.alwaysHasClosingToken() ? 1 : 0;
			for (DocResult docResult : docs) {
				Document doc = docResult.getDocument();
				row.clear();
				
				// Pid field, use lucene doc id if not provided
				if (pidField != null && doc.get(pidField) != null)
					row.add(doc.get(pidField));
				else 
					row.add(Integer.toString(docResult.getDocId())); 
				
				row.add(Integer.toString(docResult.getNumberOfHits()));
				
				// Length field, if applicable
				if (tokenLengthField != null)
					row.add(Integer.toString(Integer.parseInt(doc.get(tokenLengthField)) - subtractFromLength)); // lengthInTokens
				
				// other fields in order of appearance
				for (String fieldId : metadataFieldIds) {
					row.add(doc.get(fieldId));
				}
				
				printer.printRecord(row);
			}
			
			printer.flush();
			ds.plain(printer.getOut().toString());
		} catch (IOException e) {
			// TODO proper message
			throw new InternalServerError("Cannot write response", 1337);
		}
	}

	@Override
	public int handle(DataStream ds) throws BlsException {
		Pair<DocResults, DocGroups> result = getDocs();
		if (result.getLeft() != null) 
			writeDocs(result.getLeft(), (DataStreamPlain) ds);
		else 
			writeGroups(result.getRight(), (DataStreamPlain) ds);
		
		return HTTP_OK;
	}

	@Override
	public DataFormat getOverrideType() {
		return DataFormat.CSV;
	}
	
	@Override
	protected boolean isDocsOperation() {
		return true;
	}
}


