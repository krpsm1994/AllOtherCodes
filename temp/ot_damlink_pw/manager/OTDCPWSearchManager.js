(function(exports){

    var OTDCPWSearchManager = exports.OTDCPWSearchManager = function OTDCPWSearchManager(){};

    var searchResultsFacetsQue = [];
    var searchResultFacets = [];

    OTDCPWSearchManager.getFolderAssetsByKeyword = function getFolderAssetsByKeyword(filterTerm, pageNumber, pageSize, preferenceID, extraFields, data, callback)
	{
		var pageProperties = data.pageProperties;
		pageNumber = +(pageNumber);
		pageSize = +(pageSize);
		var serviceUrl = otui.service + "/search/text";
		var params = [];
		var facetRestrictions= SearchManager.removeColorFacetFromFacetList(JSON.parse(data.appliedFacets) ||{}); 
		var filterByRestrictions = SearchManager.extractFilterRestrictions(facetRestrictions);

		if(filterTerm)
		{
			if(filterTerm == SearchManager.SHOW_SEARCH_TOKEN)
				return callback([], 0, true);
			else
			{
				if( !filterByRestrictions )
				{
					params.push("keyword_query=" + encodeURIComponent(filterTerm));
				}
				else{
					params.push("keyword_query=" + encodeURIComponent("(" + filterTerm + ")" + (filterByRestrictions ? (" and " + filterByRestrictions[0].value_list.join(" and ")) : "")));
				}	
			}
		}
		else if(filterByRestrictions)
		{
			if(filterByRestrictions[0].value_list.length > 1)
			{
				params.push("keyword_query=" + encodeURIComponent(filterByRestrictions[0].value_list.join(" and ")));	
			}
			else 
			{	
				params.push("keyword_query=" + encodeURIComponent(filterByRestrictions[0].value_list[0].slice(1,-1)));
			}
		}
			
		params.push("load_type=metadata&load_multilingual_values=true&level_of_detail=slim&after=" + pageNumber * pageSize + "&limit=" + pageSize + "&multilingual_language_code="+data.metadataLocale);

		if(data.nodeID)
		{
			var folderFilterType = "direct";
			params.push("folder_filter_type=" + folderFilterType + "&folder_filter=" + data.nodeID);
		}

		if(data.searchConfigID && data.searchConfigID != "none")
			params.push("search_config_id=" + data.searchConfigID);
		
		if(data.searchScopeID && data.searchScopeID != "none")
			params.push("keyword_scope_id=" + data.searchScopeID);

		if(preferenceID)
			params.push("preference_id=" + encodeURIComponent(preferenceID));
	
		if (extraFields && extraFields.length) {
			if(!extraFields.includes("ARTESIA.FIELD.TAG")) {
				extraFields.push('ARTESIA.FIELD.TAG');
			}
		}
		else {
			extraFields = ['ARTESIA.FIELD.TAG'];
		}
		params.push("metadata_to_return=" + encodeURIComponent(extraFields.join(",")));		
		
		var pageProperties = data.pageProperties;
		if (pageProperties.sortPrefix != "undefined" && pageProperties.sortPrefix != "" && pageProperties.sortField && pageProperties.sortField != "undefined" && pageProperties.sortField != "" && pageProperties.sortField != PageManager.relevanceID)
    	{
    		var prefix = pageProperties.sortPrefix;
    		if (prefix && prefix[prefix.length-1] != "_")
    			prefix += "_";

    		// params.push("sort=" + prefix + pageProperties.sortField);
            //workaround; sortField is somehow set to default sort, NAME.
            params.push("sort=" + prefix + PageManager.DEAFAULT_CHECKED_OUT_SORT_FIELD);
    	}

        if(OTDCPWFacetsManager.currentFacetConfiguration) {
            params.push("facet_config_id=" + OTDCPWFacetsManager.currentFacetConfiguration.id);
        } else {
			return;
		}

		if(facetRestrictions.facets.length)
			params.push('facet_restriction_list=' + encodeURIComponent('{"facet_restriction_list":{"facet_field_restriction":' + JSON.stringify(facetRestrictions.facets) + '}}'));

		SearchManager.lastPerformedSearchData = data;

		otui.post(serviceUrl, params.join("&"), otui.contentTypes.formData, function(response, status, success)
        {
            var facetsResults = [];
            var id = data.nodeID;

			if (!success)
			{
                searchResultFacets[id] = [];

				while(searchResultsFacetsQue[id] && Array.isArray(searchResultsFacetsQue[id]) && searchResultsFacetsQue[id].length)
				{
					searchResultsFacetsQue[id].pop()(facetsResults);
				}

				callback(response, 0, false);
			}
			else
			{

				var totalResults = 0;
				var results = [];

				if(response && response.search_result_resource)
				{
					results = response.search_result_resource.asset_list || [];

					facetsResults = response.search_result_resource.search_result.facet_field_response_list || [];

                    searchResultFacets[id] = facetsResults;

					totalResults = response.search_result_resource && (response.search_result_resource.search_result.total_hit_count || 0);
				}

				while(searchResultsFacetsQue[id] && Array.isArray(searchResultsFacetsQue[id]) && searchResultsFacetsQue[id].length)
				{
					searchResultsFacetsQue[id].pop()(facetsResults);
				}

				AssetManager.prepareGalleryMetadata(results);
				
				if(success && !data.forFacetsOnly)
				{
					var pwResultsView = otui.main.getChildView(OTDCBWHomeView) || otui.main.getChildView(OTDCPWHomeView) || ( otui.main.getChildView(OTDCPWAssignToProdWrapper) && otui.main.getChildView(OTDCPWAssignToProdWrapper).getChildView(OTDCPWAssignToProductView));
					var folderFilterType = (data.searchConfigID && data.searchConfigID != "none") ? "all" : "direct";
					if(filterTerm != pwResultsView.storedProperty("searchID") || folderFilterType != pwResultsView.storedProperty("folderFilterType") || data.nodeID != pwResultsView.storedProperty("folderFilterId"))
						pwResultsView.allowServiceDataReload = false;
					pwResultsView.storedProperty("searchID", filterTerm);
					pwResultsView.storedProperty("folderFilterType", folderFilterType);
					pwResultsView.storedProperty("folderFilterId", data.nodeID);
					pwResultsView.selectionContext = {'type' : 'com.artesia.asset.selection.SearchSelectionContext',
									'map' : {'keyword_query' : 'searchID', "folder_filter_type" :'folderFilterType', "folder_filter_id" :  'folderFilterId'},
									'alwaysUse' : false, 'facetRestrictionList' : data.appliedFacets
									};
					if(!pwResultsView.allowServiceDataReload)
					{
						xtag.requestFrame(function()
						{
							pwResultsView.allowServiceDataReload = true;
						});
					}
				}
				
				callback(results, totalResults, success);
			}
        });
        
	};

    OTDCPWSearchManager.clearSearchForKeyword = function(keyword)
    {
    	searchResultFacets[keyword] = null;
    };

    OTDCPWSearchManager.getSearchResultFacetsByKeyword = function(keyword, callback)
    {
    	if(searchResultFacets[keyword])
    	{
    		callback(searchResultFacets[keyword]);
    	}
    	else
    	{
    		if(!searchResultsFacetsQue[keyword] || !Array.isArray(searchResultsFacetsQue[keyword]) )
    			searchResultsFacetsQue[keyword] = [];

    		searchResultsFacetsQue[keyword].push(callback);
    	}
    };

})(window);