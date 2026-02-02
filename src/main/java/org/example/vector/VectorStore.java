package org.example.vector;

import java.util.List;
import java.util.Map;

/**
 * Interface for vector storage operations.
 */
public interface VectorStore {

    /**
     * Connect to the vector store.
     */
    void connect() throws VectorStoreException;

    /**
     * Check if connected.
     */
    boolean isConnected();

    /**
     * Close the connection.
     */
    void close();

    /**
     * Ensure the collection exists, create if not.
     *
     * @param collectionName Name of the collection
     * @param vectorSize Dimension of vectors
     */
    void ensureCollection(String collectionName, int vectorSize) throws VectorStoreException;

    /**
     * Delete a collection if it exists.
     *
     * @param collectionName Name of the collection
     */
    void deleteCollection(String collectionName) throws VectorStoreException;

    /**
     * Insert or update a single point.
     *
     * @param collectionName Name of the collection
     * @param point The point to upsert
     */
    void upsert(String collectionName, VectorPoint point) throws VectorStoreException;

    /**
     * Insert or update multiple points in batch.
     *
     * @param collectionName Name of the collection
     * @param points The points to upsert
     */
    void upsertBatch(String collectionName, List<VectorPoint> points) throws VectorStoreException;

    /**
     * Search for similar vectors.
     *
     * @param collectionName Name of the collection
     * @param queryVector The query vector
     * @param limit Maximum number of results
     * @return List of search results
     */
    List<SearchResult> search(String collectionName, float[] queryVector, int limit) 
        throws VectorStoreException;

    /**
     * Search for similar vectors with filters.
     *
     * @param collectionName Name of the collection
     * @param queryVector The query vector
     * @param limit Maximum number of results
     * @param filters Payload filters
     * @return List of search results
     */
    List<SearchResult> search(String collectionName, float[] queryVector, int limit, 
        Map<String, Object> filters) throws VectorStoreException;

    /**
     * Delete points by IDs.
     *
     * @param collectionName Name of the collection
     * @param ids Point IDs to delete
     */
    void delete(String collectionName, List<String> ids) throws VectorStoreException;

    /**
     * Delete points by filter.
     *
     * @param collectionName Name of the collection
     * @param filters Payload filters for deletion
     */
    void deleteByFilter(String collectionName, Map<String, Object> filters) 
        throws VectorStoreException;

    /**
     * Get collection info.
     *
     * @param collectionName Name of the collection
     * @return Collection info map
     */
    Map<String, Object> getCollectionInfo(String collectionName) throws VectorStoreException;

    /**
     * Count points in collection.
     *
     * @param collectionName Name of the collection
     * @return Number of points
     */
    long count(String collectionName) throws VectorStoreException;
}
