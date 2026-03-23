(function(exports)
{
	var OTDCPWFacetsManager = exports.OTDCPWFacetsManager = function OTDCPWFacetsManager(){};
	OTDCPWFacetsManager.defaultFacetConfiguration = null;
	// OTDCPWFacetsManager.colorConfiguration = null;
	// OTDCPWFacetsManager.colorPaletteTranslated = otui.tr("Color");
	// OTDCPWFacetsManager.colorPalette = "Color";
	// OTDCPWFacetsManager.blackwhiteColor = "BLACK_AND_WHITE";
	// OTDCPWFacetsManager.ignoreColorFilter = false;
	
	//OTDCPWFacetsManager.read = function read(searchID, callback)
	OTDCPWFacetsManager.currentFacetConfiguration = undefined;
	OTDCPWFacetsManager.allFacetConfiguration = undefined;

	cachedFacetConfiguration = [];

	OTDCPWFacetsManager.readFacets = function readFacets(facetConfigurationName, defaultFacetName, callback) {
		var serviceUrl = otui.service + "/facetconfigurations";
		
		otui.get(serviceUrl, undefined, otui.contentTypes.json, function(response, status, success)
        {
			if(!success)
			{
				callback(response, false);
			}
			else
			{
				OTDCPWFacetsManager.allFacetConfiguration = response.facet_configurations_resource.facet_configuration_list;
				var retrievedCurrentFacetConfiguration = false;
				var retrievedDefaultFacetConfiguration = false;

				for(var i = 0; i < response.facet_configurations_resource.facet_configuration_list.length; i++)
				{
					if(response.facet_configurations_resource.facet_configuration_list[i].name === facetConfigurationName)
						{
							OTDCPWFacetsManager.currentFacetConfiguration = response.facet_configurations_resource.facet_configuration_list[i];
							retrievedCurrentFacetConfiguration = true;
							// facetColorCheck = response.facet_configurations_resource.facet_configuration_list[i].show_color_filter;
							if(retrievedDefaultFacetConfiguration)
							{
								break;
							}
						}
					
					if(response.facet_configurations_resource.facet_configuration_list[i].name == defaultFacetName)
						{
							OTDCPWFacetsManager.defaultFacetConfiguration = response.facet_configurations_resource.facet_configuration_list[i];
							retrievedDefaultFacetConfiguration = true;
							// facetColorCheck = response.facet_configurations_resource.facet_configuration_list[i].show_color_filter;
							if(retrievedCurrentFacetConfiguration)
							{
								break;
							}
						}
					
				}

				if(!OTDCPWFacetsManager.currentFacetConfiguration)
				{
					OTDCPWFacetsManager.currentFacetConfiguration = OTDCPWFacetsManager.defaultFacetConfiguration;
				}

				callback(response, true);

			}
	    	
		});
	};

	OTDCPWFacetsManager.read = function read(data, facetConfigurationName, defaultFacetName, callback)
    {
		if(!(OTDCPWFacetsManager.currentFacetConfiguration && OTDCPWFacetsManager.currentFacetConfiguration.name == facetConfigurationName)){
			//Clear variable
			OTDCPWFacetsManager.currentFacetConfiguration = undefined;
			OTDCPWFacetsManager.readFacets(facetConfigurationName, defaultFacetName, function() {
				var nodeID = data.nodeID;
				var searchID = nodeID ;
				
				OTDCPWSearchManager.getSearchResultFacetsByKeyword(searchID, function(facets, pluginId){
					callback(facets, pluginId);
				});
			});
		}else{
			var nodeID = data.nodeID;
			var searchID = nodeID ;
			
			OTDCPWSearchManager.getSearchResultFacetsByKeyword(searchID, function(facets, pluginId){
				callback(facets, pluginId);
			});
		}
	
	};

	OTDCPWFacetsManager.readForAssets = function read(data, facetConfigurationName, defaultFacetName, callback)
    {
		if(!OTDCPWFacetsManager.currentFacetConfiguration){
			OTDCPWFacetsManager.readFacets(facetConfigurationName, defaultFacetName, function() {
				var nodeID = data.nodeID;
				var searchID = nodeID ;
				
				OTDCPWAssetSearchManager.getSearchResultFacetsByKeyword(searchID, function(facets, pluginId){
					callback(facets, pluginId);
				});
			});
		}else{
			var nodeID = data.nodeID;
			var searchID = nodeID ;
			
			OTDCPWAssetSearchManager.getSearchResultFacetsByKeyword(searchID, function(facets, pluginId){
				callback(facets, pluginId);
			});
		}
	
	};
	
	/**
	 * This function call is to fire api to retrieve color to be shown in ui.
	 */
	 OTDCPWFacetsManager.getColor = function(callback)
	{
		var serviceUrl = otui.service + "/colorfilters";

		otui.get(serviceUrl, undefined, otui.contentTypes.json, function(response, status, success)
        {
			
			if(success)
			{
				var colorList = ((response || {} ).color_filters_resource || {}).color_filter_list || [];
				OTDCPWFacetsManager.colorConfiguration = colorList;
			}
			if(callback) {
				callback();
			}				    	
		});

	};
	
	/**
	 * This function will check whether to show color palette or not
	 * @param {*} allowColorPalette this is system setting value 
	 * @param {*} facetColorCheck this is facet configuration check 
	 * @param {*} searchID  this value is for search id (advance search id )
	 */
	 OTDCPWFacetsManager.showColorFacet = function(allowColorPalette , facetColorCheck , searchID	,callback)
	{
		if(allowColorPalette.toLowerCase() === "true" && facetColorCheck)
		{
			if(searchID !== advancesearchUserid && searchID !== advancesearchFolderid) // this id 1 and 2  is for users and folder search
			{
				OTDCPWFacetsManager.getColor(callback);
			}

		}
		else
		{
			OTDCPWFacetsManager.colorConfiguration = null;
			if(callback) {
				callback();
			}
		}

	};
	
	OTDCPWFacetsManager.getIntervalLabels = function(names, intervals, description)
	{
		intervals.forEach(function(interval)
		{
			(interval.numeric_interval_list || interval.date_interval_list).forEach(function(obj)
			{
				var tmp={};
				tmp.purpose = description;
				tmp.name = obj.interval_label;
				names.push(tmp);
			});
		});
	};
	
	OTDCPWFacetsManager.getTextForTranslation = function()
    {
    	return new Promise(function(resolve, reject)
    	{
	    	var text = [];
			var serviceUrl = otui.service + "/intervalprofiles";
			otui.OTMMHelpers.getDBresponse(serviceUrl).then(function(response)
			{
				OTDCPWFacetsManager.getIntervalLabels(text, response.interval_profiles_resource.interval_profile_list, "Interval profiles");	
				resolve(text);
			}).catch(function(err)
			{
				reject(err);
			});
    	});
	};

	OTDCPWFacetsManager.updateFacetConfiguration = function(facetConfigurationName,defaultFacetName){
		OTDCPWFacetsManager.currentFacetConfiguration = undefined;
		var retrievedCurrentFacetConfiguration = false;
		var retrievedDefaultFacetConfiguration = false;
		// var defaultFacetName = "PW.FACET.DEFAULT.CONFIG";
		for(var i = 0; i < OTDCPWFacetsManager.allFacetConfiguration.length; i++)
		{
			if(OTDCPWFacetsManager.allFacetConfiguration[i].name === facetConfigurationName)
				{
					OTDCPWFacetsManager.currentFacetConfiguration = OTDCPWFacetsManager.allFacetConfiguration[i];
					retrievedCurrentFacetConfiguration = true;
					// facetColorCheck = OTDCPWFacetsManager.allFacetConfiguration[i].show_color_filter;
					if(retrievedDefaultFacetConfiguration)
					{
						break;
					}
				}						
			if(OTDCPWFacetsManager.allFacetConfiguration[i].name == defaultFacetName)
				{
					OTDCPWFacetsManager.defaultFacetConfiguration = OTDCPWFacetsManager.allFacetConfiguration[i];
					retrievedDefaultFacetConfiguration = true;
					// facetColorCheck = OTDCPWFacetsManager.allFacetConfiguration[i].show_color_filter;
					if(retrievedCurrentFacetConfiguration)
					{
						break;
					}
				}						
		}

		if(!OTDCPWFacetsManager.currentFacetConfiguration)
		{
			OTDCPWFacetsManager.currentFacetConfiguration = OTDCPWFacetsManager.defaultFacetConfiguration;
		}
	}

   otui.registerService('pwfacetsResult', OTDCPWFacetsManager);
})(window);