#!/usr/bin/env python3
import sys

with open('services/collaborative_service.py', 'r', encoding='utf-8') as f:
    lines = f.readlines()

# Find and fix line 1093 onwards
in_problematic_section = False
output_lines = []
skip_until = -1

for i, line in enumerate(lines):
    if i < skip_until:
        continue
    
    # Find the problematic section by looking for "Candidate artists"
    if 'Candidate artists: {len(artist_track_count)}' in line:
        # Found it! Replace the entire section
        output_lines.append('            logger.info(f"[CF]   Shared artist matches: {collection_metrics[\'shared_artist_matched\']}")\n')
        output_lines.append('            \n')
        output_lines.append('            if not track_scores:\n')
        output_lines.append('                logger.warning(f"[CF] ✗ No candidates found")\n')
        output_lines.append('                return []\n')
        output_lines.append('            \n')
        output_lines.append('            # Get unique artists in candidate pool\n')
        output_lines.append('            unique_artists_set = set()\n')
        output_lines.append('            for track_info in track_scores.values():\n')
        output_lines.append('                for artist in track_info.get(\'artists\', []):\n')
        output_lines.append('                    unique_artists_set.add(artist)\n')
        output_lines.append('            logger.info(f"[CF]   Unique artists: {len(unique_artists_set)}")\n')
        output_lines.append('            \n')
        output_lines.append('            # STEP 4: Apply diversity constraints (max 2/artist)\n')
        output_lines.append('            logger.info(f"[CF] ━━━ STEP 4: Diversity (max 2 per artist) ━━━")\n')
        output_lines.append('            \n')
        output_lines.append('            # Sort candidates by score\n')
        output_lines.append('            sorted_candidates = sorted(\n')
        output_lines.append('                track_scores.items(),\n')
        output_lines.append('                key=lambda x: x[1][\'score\'],\n')
        output_lines.append('                reverse=True\n')
        output_lines.append('            )\n')
        output_lines.append('            \n')
        output_lines.append('            artist_counts = defaultdict(int)\n')
        output_lines.append('            recommendations = []\n')
        output_lines.append('            \n')
        output_lines.append('            for video_id, track_info in sorted_candidates:\n')
        output_lines.append('                if not track_info[\'data\']:\n')
        output_lines.append('                    continue\n')
        output_lines.append('                \n')
        output_lines.append('                artists = track_info.get(\'artists\', [])\n')
        output_lines.append('                primary_artist = artists[0] if artists else \'Unknown\'\n')
        output_lines.append('                \n')
        output_lines.append('                # Enforce diversity: max 2 per artist\n')
        output_lines.append('                if artist_counts[primary_artist] >= 2:\n')
        output_lines.append('                    continue\n')
        output_lines.append('                \n')
        output_lines.append('                rec = {\n')
        output_lines.append('                    \'videoId\': video_id,\n')
        output_lines.append('                    \'title\': track_info[\'data\'].get(\'title\', \'\'),\n')
        output_lines.append('                    \'artists\': artists,\n')
        output_lines.append('                    \'thumbnail\': track_info[\'data\'].get(\'thumbnail\', \'\'),\n')
        output_lines.append('                    \'album\': track_info[\'data\'].get(\'album\', \'\'),\n')
        output_lines.append('                    \'duration\': track_info[\'data\'].get(\'duration\'),\n')
        output_lines.append('                    \'cf_score\': track_info[\'score\']\n')
        output_lines.append('                }\n')
        output_lines.append('                \n')
        output_lines.append('                recommendations.append(rec)\n')
        output_lines.append('                artist_counts[primary_artist] += 1\n')
        output_lines.append('                \n')
        output_lines.append('                if len(recommendations) >= self.MAX_RECOMMENDATIONS:\n')
        output_lines.append('                    break\n')
        output_lines.append('            \n')
        output_lines.append('            logger.info(f"[CF] ✓ After diversity: {len(recommendations)} tracks")\n')
        output_lines.append('            logger.info(f"[CF] ✓ Artist distribution: {dict(artist_counts)}")\n')
        output_lines.append('            \n')
        output_lines.append('            # STEP 5: Fallback if insufficient results\n')
        
        # Skip the old problematic code until we find "STEP 5"
        j = i + 1
        while j < len(lines):
            if '# STEP 5: Guarantee minimum results' in lines[j]:
                skip_until = j + 1
                break
            j += 1
    else:
        output_lines.append(line)

with open('services/collaborative_service.py', 'w', encoding='utf-8') as f:
    f.writelines(output_lines)

print("✓ Fixed artist_track_count and implemented diversity layer")
