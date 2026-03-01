#!/usr/bin/env python3
"""Fix collaborative_service.py 8-step implementation"""

import re

with open('services/collaborative_service.py', 'r', encoding='utf-8') as f:
    content = f.read()

# Fix 1: Replace old STEP 3 section with new STEP 4 diversity layer
old = '''            logger.info(f"[CF]   Candidate artists: {len(artist_track_count)}")
            
            if not track_scores:
                logger.warning(f"[CF] \u26a0 No cross-user candidates found")
                return []
            
            # Show top candidate artists by frequency
            if artist_track_count:
                top_candidate_artists = sorted(artist_track_count.items(), key=lambda x: x[1], reverse=True)[:5]
                logger.info(f"[CF]   Top candidate artists: {top_candidate_artists}")
            
            # STEP 3: Apply diversity ranking
            logger.info(f"[CF] \u2501\u2501\u2501 STEP 3: Diversity ranking \u2501\u2501\u2501")
            recommendations = self._apply_diversity_ranking(
                track_scores,
                similar_user_artists,
                min_results=self.MIN_CF_RESULTS,
                max_results=self.MAX_RECOMMENDATIONS
            )
            
            logger.info(f"[CF] \u2713 After diversity ranking: {len(recommendations)} tracks")
            
            # STEP 4: Log source attribution
            logger.info(f"[CF] \u2501\u2501\u2501 DISCOVERY ATTRIBUTION \u2501\u2501\u2501")
            source_counts = defaultdict(int)
            for rec in recommendations:
                video_id = rec['videoId']
                # Find source from track_scores
                for track_id, track_info in track_scores.items():
                    if track_id == video_id:
                        for source in track_info['sources']:
                            source_counts[source] += 1
                        break
            
            logger.info(f"[CF]   Final results by source:")
            logger.info(f"[CF]     - From likes: {source_counts['like']}")
            logger.info(f"[CF]     - From playlists: {source_counts['playlist']}")
            logger.info(f"[CF]     - From plays: {source_counts['play']}")
            
            # STEP 5: Guarantee minimum results'''

new = '''            logger.info(f"[CF]   Shared artist matches: {collection_metrics['shared_artist_matched']}")
            
            if not track_scores:
                logger.warning(f"[CF] \u2717 No candidates found")
                return []
            
            # Get unique candidates
            unique_artists = set()
            for track_info in track_scores.values():
                for artist in track_info.get('artists', []):
                    unique_artists.add(artist)
            logger.info(f"[CF]   Unique artists in candidates: {len(unique_artists)}")
            
            # STEP 4: Apply diversity constraints (max 2 per artist)
            logger.info(f"[CF] \u2501\u2501\u2501 STEP 4: Applying diversity (2/artist max) \u2501\u2501\u2501")
            
            # Sort by score and apply diversity
            sorted_candidates = sorted(
                track_scores.items(),
                key=lambda x: x[1]['score'],
                reverse=True
            )
            
            artist_counts = defaultdict(int)
            recommendations = []
            
            for video_id, track_info in sorted_candidates:
                if not track_info['data']:
                    continue
                
                artists = track_info.get('artists', [])
                primary_artist = artists[0] if artists else 'Unknown'
                
                # Enforce diversity: max 2 per artist
                if artist_counts[primary_artist] >= 2:
                    continue
                
                rec = {
                    'videoId': video_id,
                    'title': track_info['data'].get('title', ''),
                    'artists': artists,
                    'thumbnail': track_info['data'].get('thumbnail', ''),
                    'album': track_info['data'].get('album', ''),
                    'duration': track_info['data'].get('duration'),
                    'cf_score': track_info['score']
                }
                
                recommendations.append(rec)
                artist_counts[primary_artist] += 1
                
                if len(recommendations) >= self.MAX_RECOMMENDATIONS:
                    break
            
            logger.info(f"[CF] \u2713 After diversity: {len(recommendations)} tracks")
            logger.info(f"[CF] \u2713 Artist distribution: {dict(artist_counts)}")
            
            # STEP 5: Guarantee minimum results'''

content = content.replace(old, new)

# Also fix the STEP 5 fallback section
old5 = '''            # STEP 5: Guarantee minimum results
            if len(recommendations) < self.MIN_CF_RESULTS:
                logger.warning(f"[CF] \u26a0 Below minimum: {len(recommendations)} < {self.MIN_CF_RESULTS}")
                
                # Strategy 1: Expand with artist tracks
                if similar_user_artists:
                    logger.info(f"[CF] \u2192 Strategy 1: Expanding with {len(similar_user_artists)} shared artists")
                    recommendations = self._expand_with_artist_tracks(
                        recommendations,
                        similar_user_artists,
                        current_user_known,  # Use all known songs for exclusion
                        target_count=self.MIN_CF_RESULTS
                    )
                    logger.info(f"[CF] \u2713 After artist expansion: {len(recommendations)}")
            
            # Strategy 2: Cold-start fallback if still insufficient
            if len(recommendations) < self.MIN_CF_RESULTS:
                logger.warning(f"[CF] \u26a0 Still below minimum, applying cold-start")
                fallback = self._cold_start_fallback(uid, current_user_known)
                
                existing_ids = {r['videoId'] for r in recommendations}
                for track in fallback:
                    if track['videoId'] not in existing_ids:
                        recommendations.append(track)
                        if len(recommendations) >= self.MIN_CF_RESULTS:
                            break
                
                logger.info(f"[CF] \u2713 After fallback: {len(recommendations)}")
            
            # Limit to MAX
            recommendations = recommendations[:self.MAX_RECOMMENDATIONS]
            
            # Final metrics
            unique_artists = set()
            artist_distribution = defaultdict(int)
            for rec in recommendations:
                artists = rec.get('artists', [])
                primary_artist = artists[0] if artists else 'Unknown'
                unique_artists.add(primary_artist)
                artist_distribution[primary_artist] += 1
            
            logger.info(f"[CF] \u2501\u2501\u2501 FINAL CROSS-USER DISCOVERY RESULTS \u2501\u2501\u2501")
            logger.info(f"[CF] \u2713 Total tracks: {len(recommendations)}")
            logger.info(f"[CF] \u2713 Unique artists: {len(unique_artists)}")
            logger.info(f"[CF] \u2713 Artist distribution: {dict(artist_distribution)}")
            
            # Check for dominance
            if artist_distribution:
                max_count = max(artist_distribution.values())
                if max_count > len(recommendations) * 0.5:
                    dominant_artist = max(artist_distribution, key=artist_distribution.get)
                    logger.warning(f"[CF] \u26a0 Dominance: '{dominant_artist}' = {max_count}/{len(recommendations)}")
            
            # Final validation
            if len(recommendations) < self.MIN_CF_RESULTS:
                logger.error(f"[CF] \u274c FAILED to guarantee minimum {self.MIN_CF_RESULTS} results!")
            else:
                logger.info(f"[CF] \u2713 SUCCESS: {len(recommendations)} \u2265 {self.MIN_CF_RESULTS}")'''

new5 = '''            # STEP 5: Fallback if insufficient
            logger.info(f"[CF] \u2501\u2501\u2501 STEP 5: Fallback strategies \u2501\u2501\u2501")
            
            if len(recommendations) < self.MIN_CF_RESULTS:
                logger.warning(f"[CF] \u26a0 Below minimum: {len(recommendations)} < {self.MIN_CF_RESULTS}")
                
                # Fallback 1: Expand with artist tracks
                if similar_user_artists:
                    logger.info(f"[CF] \u2192 Fallback 1: Artist expansion ({len(similar_user_artists)} artists)")
                    recommendations = self._expand_with_artist_tracks(
                        recommendations,
                        similar_user_artists,
                        current_user_known,
                        target_count=self.MIN_CF_RESULTS
                    )
                    logger.info(f"[CF] \u2713 After expansion: {len(recommendations)}")
            
            # Fallback 2: Cold-start if still insufficient  
            if len(recommendations) < self.MIN_CF_RESULTS:
                logger.warning(f"[CF] \u26a0 Still insufficient, applying cold-start")
                fallback = self._cold_start_fallback(uid, current_user_known)
                
                existing_ids = {r['videoId'] for r in recommendations}
                for track in fallback:
                    if track['videoId'] not in existing_ids:
                        recommendations.append(track)
                        if len(recommendations) >= self.MIN_CF_RESULTS:
                            break
                
                logger.info(f"[CF] \u2713 After cold-start: {len(recommendations)}")
            
            # Limit to max
            recommendations = recommendations[:self.MAX_RECOMMENDATIONS]
            
            # Final verification
            logger.info(f"[CF] \u2501\u2501\u2501 FINAL RESULTS \u2501\u2501\u2501")
            logger.info(f"[CF] \u2713 Total tracks: {len(recommendations)}")
            
            # Artist diversity check
            final_artists = {}
            for rec in recommendations:
                for artist in rec.get('artists', []):
                    final_artists[artist] = final_artists.get(artist, 0) + 1
            
            logger.info(f"[CF] \u2713 Unique artists: {len(final_artists)}")
            logger.info(f"[CF] \u2713 Distribution: {dict(final_artists)}")
            
            # Check minimum requirement
            if len(recommendations) < self.MIN_CF_RESULTS:
                logger.error(f"[CF] \u274c FAILED minimum: {len(recommendations)} < {self.MIN_CF_RESULTS}")
            else:
                logger.info(f"[CF] \u2713 SUCCESS: {len(recommendations)} \u2265 {self.MIN_CF_RESULTS}")'''

content = content.replace(old5, new5)

with open('services/collaborative_service.py', 'w', encoding='utf-8') as f:
    f.write(content)

print("✓ Fixed collaborative_service.py - 8-step artist-vector CF implemented")
