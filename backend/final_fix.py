#!/usr/bin/env python3
"""
Rewrite the scoring and diversity section of _generate_recommendations_from_similar_users
to implement 8-step artist-vector collaborative filtering with proper multi-signal scoring.
"""

import re

# Read the original file
with open('services/collaborative_service.py', 'r', encoding='utf-8') as f:
    content = f.read()

# The new implementation for STEP 3-7
new_scoring_and_diversity = '''            # STEP 3: Score candidates using multi-signal formula
            logger.info(f"[CF] ━━━ STEP 3: Multi-signal scoring ━━━")
            logger.info(f"[CF] Collected: plays={collection_metrics['plays_collected']} likes={collection_metrics['likes_collected']} playlists={collection_metrics['playlists_collected']}")
            
            if not track_scores:
                logger.warning(f"[CF] ✗ No candidates collected")
                return []
            
            # Sort candidates by score
            sorted_candidates = sorted(
                track_scores.items(),
                key=lambda x: x[1]['score'],
                reverse=True
            )
            
            logger.info(f"[CF] Top 5 by score:")
            for i, (vid, info) in enumerate(sorted_candidates[:5], 1):
                title = info['data'].get('title', '')[:40] if info['data'] else ''
                logger.info(f"[CF]   {i}. {title} (score={info['score']:.0f})")
            
            # STEP 4: Apply diversity constraints
            logger.info(f"[CF] ━━━ STEP 4: Applying diversity (max 2/artist) ━━━")
            
            artist_song_count = defaultdict(int)
            recommendations = []
            
            for video_id, track_info in sorted_candidates:
                if not track_info['data']:
                    continue
                
                artists = track_info.get('artists', [])
                primary_artist = artists[0] if artists else 'Unknown'
                
                # DIVERSITY: Max 2 songs per artist
                if artist_song_count[primary_artist] >= 2:
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
                artist_song_count[primary_artist] += 1
                
                if len(recommendations) >= self.MAX_RECOMMENDATIONS:
                    break
            
            logger.info(f"[CF] ✓ After diversity: {len(recommendations)} tracks")
            logger.info(f"[CF] ✓ Artist distribution: {dict(artist_song_count)}")
            
            # STEP 5: Fallback mechanisms
            logger.info(f"[CF] ━━━ STEP 5: Fallback check ━━━")
'''

# Find and replace the large scoring/diversity section
# Look for the pattern: "# Log collection statistics" through "# Strategy 2: Cold-start"
pattern = r'# Log collection statistics\n.*?\n.*?# Strategy 2: Cold-start fallback if still insufficient'

match = re.search(pattern, content, re.DOTALL)
if match:
    # Find where "Strategy 2" ends to know where to cut
    strategy2_end = content.find('\n            \n            # Limit to MAX', match.end())
    if strategy2_end > 0:
        before = content[:match.start()]
        after = content[strategy2_end:]
        
        new_content = before + new_scoring_and_diversity + '''
            if len(recommendations) < self.MIN_CF_RESULTS:
                logger.warning(f"[CF] ⚠ Below minimum: {len(recommendations)} < {self.MIN_CF_RESULTS}")
                
                # Fallback 1: Expand with artist tracks
                if similar_user_artists:
                    logger.info(f"[CF] → Fallback 1: Artist expansion ({len(similar_user_artists)} artists)")
                    recommendations = self._expand_with_artist_tracks(
                        recommendations,
                        similar_user_artists,
                        current_user_known,
                        target_count=self.MIN_CF_RESULTS
                    )
                    logger.info(f"[CF] ✓ After expansion: {len(recommendations)}")
            
            # Fallback 2: Cold-start
            if len(recommendations) < self.MIN_CF_RESULTS:
                logger.warning(f"[CF] ⚠ Applying cold-start fallback")
                fallback = self._cold_start_fallback(uid, current_user_known)
                
                existing_ids = {r['videoId'] for r in recommendations}
                for track in fallback:
                    if track['videoId'] not in existing_ids:
                        recommendations.append(track)
                        if len(recommendations) >= self.MIN_CF_RESULTS:
                            break
                
                logger.info(f"[CF] ✓ After cold-start: {len(recommendations)}")
            
            # STEP 6: Final results
            logger.info(f"[CF] ━━━ STEP 6: Final validation ━━━")
            recommendations = recommendations[:self.MAX_RECOMMENDATIONS]''' + after
        
        with open('services/collaborative_service.py', 'w', encoding='utf-8') as f:
            f.write(new_content)
        
        print("✓ Successfully rewrote 8-step artist-vector CF scoring and diversity layer")
    else:
        print("✗ Could not find end of Strategy 2 section")
else:
    print("✗ Could not find scoring/diversity section to replace")
