/**
 * Image storage plugins implementing {@link com.doqsa.backend.application.port.out.ImageStorage}.
 *
 * <p>Each provider lives in its own sub-package and is switched on by {@code doqsa.storage.provider}:
 * <ul>
 *   <li>{@code local} (default, not in {@code prod}): {@code local} package</li>
 *   <li>{@code s3}: {@code s3} package</li>
 *   <li>{@code supabase}: {@code supabase} package</li>
 * </ul>
 *
 * <p>Plugin contract, so that adding or removing a provider touches nothing outside its package:
 * <ul>
 *   <li>One {@code @Configuration} class annotated with
 *       {@code @ConditionalOnProperty(name = "doqsa.storage.provider", havingValue = "<provider>")}
 *       declares every bean of the plugin, including exactly one {@code ImageStorage}.</li>
 *   <li>Implementation classes are plain classes without stereotype annotations; any component that must be
 *       scanned (such as a controller) repeats the same condition.</li>
 *   <li>Plugins never reference each other. They may depend on the core ({@code application}, {@code global}).</li>
 *   <li>Infrastructure failures that a later attempt may fix are thrown as
 *       {@link com.doqsa.backend.application.service.OcrExceptions.Retryable}; a missing object is reported by
 *       {@code exists() == false}, not by an exception.</li>
 *   <li>New providers keep their settings under {@code doqsa.storage.<provider>.*}. The existing
 *       {@code doqsa.storage.local-directory}, {@code region} and {@code bucket} keys are kept for compatibility.</li>
 * </ul>
 */
package com.doqsa.backend.adapter.out.storage;
