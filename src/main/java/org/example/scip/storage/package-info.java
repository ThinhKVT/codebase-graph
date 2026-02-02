/**
 * SCIP index storage and versioning.
 * 
 * <p>This package provides versioned storage for SCIP index files,
 * allowing tracking of indexing history and re-processing from
 * previously generated indices.</p>
 * 
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link org.example.scip.storage.ScipIndexStorage} - Main storage manager</li>
 *   <li>{@link org.example.scip.storage.IndexMetadata} - Metadata about each index</li>
 *   <li>{@link org.example.scip.storage.IndexStatus} - Processing status enum</li>
 * </ul>
 * 
 * <h2>Storage Structure</h2>
 * <pre>
 * .codebase-graph/
 * ├── indices/                        # SCIP index files
 * │   └── {project}-{timestamp}.scip
 * └── metadata/                       # JSON metadata files
 *     └── {project}-{timestamp}.json
 * </pre>
 */
package org.example.scip.storage;
