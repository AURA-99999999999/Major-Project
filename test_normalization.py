"""
Unit tests for movie title normalization and TMDB search retry logic.

Tests the normalize_movie_name() function and extraction strategies.
"""

from services.tmdb_service import normalize_movie_name, extract_base_title


def test_normalize_movie_name():
    """Test various movie title normalization cases"""
    
    test_cases = [
        # (input, expected_output)
        ("Dilwale (Original Motion Picture Soundtrack)", "Dilwale"),
        ("Tadap (Soundtrack)", "Tadap"),
        ("Tadap (OST)", "Tadap"),
        ("Tadap (O.S.T)", "Tadap"),
        ("Songs & Dialogues (From \"Dilwale\")", "Dilwale"),
        ("Songs and Dialogues (From \"Movie Name\")", "Movie Name"),
        ("(From \"Dilwale\")", "Dilwale"),
        ("(From 'Dilwale')", "Dilwale"),
        ("Movie Name (Motion Picture Soundtrack)", "Movie Name"),
        ("Movie Name (Original Soundtrack)", "Movie Name"),
        ("Album from The Movie (Soundtrack)", "The Movie"),
        ("  Dilwale  ", "Dilwale"),  # Extra spaces
        ("", ""),  # Empty string
        (None, ""),  # None
        ("Regular Movie Name", "Regular Movie Name"),  # No suffix
    ]
    
    print("\n" + "="*70)
    print("TESTING: normalize_movie_name()")
    print("="*70)
    
    passed = 0
    failed = 0
    
    for raw_name, expected in test_cases:
        result = normalize_movie_name(raw_name)
        status = "✓ PASS" if result == expected else "✗ FAIL"
        
        if result == expected:
            passed += 1
        else:
            failed += 1
        
        print(f"\n{status}")
        print(f"  Input:    {repr(raw_name)}")
        print(f"  Expected: {repr(expected)}")
        print(f"  Got:      {repr(result)}")
    
    print("\n" + "="*70)
    print(f"Results: {passed} passed, {failed} failed out of {len(test_cases)} tests")
    print("="*70)
    
    return failed == 0


def test_extract_base_title():
    """Test base title extraction (before parenthesis)"""
    
    test_cases = [
        # (input, expected_output)
        ("Dilwale (Original Motion Picture Soundtrack)", "Dilwale"),
        ("Movie Name (From 'Another')", "Movie Name"),
        ("Simple Title", "Simple Title"),
        ("Title (with) (multiple) (parens)", "Title"),
        ("", ""),
        (None, ""),
    ]
    
    print("\n" + "="*70)
    print("TESTING: extract_base_title()")
    print("="*70)
    
    passed = 0
    failed = 0
    
    for raw_name, expected in test_cases:
        result = extract_base_title(raw_name)
        status = "✓ PASS" if result == expected else "✗ FAIL"
        
        if result == expected:
            passed += 1
        else:
            failed += 1
        
        print(f"\n{status}")
        print(f"  Input:    {repr(raw_name)}")
        print(f"  Expected: {repr(expected)}")
        print(f"  Got:      {repr(result)}")
    
    print("\n" + "="*70)
    print(f"Results: {passed} passed, {failed} failed out of {len(test_cases)} tests")
    print("="*70)
    
    return failed == 0


def test_normalization_pipeline():
    """Test the complete normalization pipeline for real-world cases"""
    
    real_world_cases = [
        "Songs & Dialogues (From \"Dilwale\")",
        "Tadap (Original Motion Picture Soundtrack)",
        "Gerua (From \"Dilwale\")",
        "Ghoomar (From \"Padmaavat\")",
        "Khalibali (From \"Padmaavat\")",
        "Bekhayali (From \"Kabali\")",
        "Ik Vaari Aa (Raees OST)",
    ]
    
    print("\n" + "="*70)
    print("REAL-WORLD TEST CASES: Normalization Pipeline")
    print("="*70)
    
    for raw_name in real_world_cases:
        normalized = normalize_movie_name(raw_name)
        base = extract_base_title(raw_name)
        
        print(f"\nInput:      {raw_name}")
        print(f"Normalized: {normalized}")
        print(f"Base Title: {base}")
        print("-" * 70)


def test_search_strategy_order():
    """Test the order of search strategies that will be tried"""
    
    print("\n" + "="*70)
    print("SEARCH STRATEGY EXECUTION ORDER")
    print("="*70)
    
    test_input = "Songs & Dialogues (From \"Dilwale\")"
    
    print(f"\nInput: {test_input}\n")
    
    # Simulate the strategy building
    normalized = normalize_movie_name(test_input)
    base = extract_base_title(test_input)
    
    search_strategies = []
    
    # Strategy 1: Normalized name
    if normalized and normalized != test_input:
        search_strategies.append(('normalized', normalized))
    
    # Strategy 2: Base title
    if base and base != test_input and base != normalized:
        search_strategies.append(('base_title', base))
    
    # Strategy 3: Original name
    search_strategies.append(('original', test_input))
    
    for i, (strategy_name, search_term) in enumerate(search_strategies, 1):
        print(f"Strategy {i} ({strategy_name}): {search_term}")
    
    print("\n" + "="*70)


if __name__ == '__main__':
    print("\n\n" + "█"*70)
    print("█ MOVIE TITLE NORMALIZATION - UNIT TESTS")
    print("█"*70)
    
    # Run all tests
    test_normalize_movie_name()
    test_extract_base_title()
    test_search_strategy_order()
    test_normalization_pipeline()
    
    print("\n" + "█"*70)
    print("█ ALL TESTS COMPLETED")
    print("█"*70 + "\n")
