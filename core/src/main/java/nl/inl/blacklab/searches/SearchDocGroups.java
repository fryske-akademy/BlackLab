package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocGroupProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.results.DocGroup;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SampleParameters;

/** A search that yields groups of hits. */
public abstract class SearchDocGroups extends SearchResults<DocGroup> {
    
    public SearchDocGroups(QueryInfo queryInfo) {
        super(queryInfo);
    }

    @Override
    public final DocGroups execute() throws InvalidQuery {
        DocGroups result = (DocGroups)getFromCache(this);
        if (result != null)
            return result;
        return notifyCache(executeInternal());
    }
    
    protected abstract DocGroups executeInternal() throws InvalidQuery;
    
    /**
     * Sort hits.
     * 
     * @param sortBy what to sort by
     * @return resulting operation
     */
    public SearchDocGroups sort(DocGroupProperty sortBy) {
        return new SearchDocGroupsSorted(queryInfo(), this, sortBy);
    }
    
    /**
     * Sample hits.
     * 
     * @param par how many hits to sample; seed
     * @return resulting operation
     */
    public SearchDocGroups sample(SampleParameters par) {
        return new SearchDocGroupsSampled(queryInfo(), this, par);
    }

    /**
     * Get hits with a certain property value.
     * 
     * @param property property to test 
     * @param value value to test for
     * @return resulting operation
     */
    public SearchDocGroups filter(DocGroupProperty property, PropertyValue value) {
        return new SearchDocGroupsFiltered(queryInfo(), this, property, value);
    }

    /**
     * Get window of hits.
     * 
     * @param first first hit to select
     * @param number number of hits to select
     * @return resulting operation
     */
    public SearchDocGroups window(int first, int number) {
        return new SearchDocGroupsWindow(queryInfo(), this, first, number);
    }
    
    @Override
    public abstract boolean equals(Object obj);
    
    @Override
    public abstract int hashCode();
    
}
