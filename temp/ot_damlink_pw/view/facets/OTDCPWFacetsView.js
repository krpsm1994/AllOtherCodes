(function (exports) {
	var NOFILTERSMESSAGE = otui.tr("No filters are available for your current search results...");

	var _removeAllFacets = function () {
		var view = otui.main.getChildView(OTDCBWHomeView) || otui.main.getChildView(OTDCPWHomeView)
		|| (otui.main.getChildView(OTDCPWAssignToProdWrapper) && otui.main.getChildView(OTDCPWAssignToProdWrapper).getChildView(OTDCPWAssignToProductView));

		OTDCPWSearchManager.clearSearchForKeyword(view.properties.nodeID);

		SelectionManager.clearSelection(view);
		var pageProperties;
		var routeName = "open";
		if (view instanceof OTDCPWHomeView) {
			pageProperties = OTDCPWHomeView.getDefaultPageProperties();
			pageProperties.sortField = "NAME";
			view.callRoute(routeName, { 'appliedFacets': JSON.stringify({ 'facets': [] }), 'currentAccordion': 0, 'pageProperties': pageProperties, 'filterTerm': "" });
		}
		if (view instanceof OTDCBWHomeView) {
			pageProperties = OTDCBWHomeView.getDefaultPageProperties();
			pageProperties.sortField = "NAME";
			view.callRoute(routeName, { 'appliedFacets': JSON.stringify({ 'facets': [] }), 'currentAccordion': 0, 'pageProperties': pageProperties, 'filterTerm': "" });
		}
		if (view instanceof OTDCPWAssignToProductView) {
			//Reload assets and facets view				
			view.properties.appliedFacets = JSON.stringify(facetRestrictions);
			view.properties.pageProperties.page = '0';
			view.reload(true);
		}
	};

	var _addFacet = function (facet) {
		var view = otui.main.getChildView(OTDCBWHomeView) || otui.main.getChildView(OTDCPWHomeView) || (otui.main.getChildView(OTDCPWAssignToProdWrapper) && otui.main.getChildView(OTDCPWAssignToProdWrapper).getChildView(OTDCPWAssignToProductView));

		var facetRestrictions = OTDCUtilities.addFacetToFacetRestrictions(view, facet);

		// //Also update sidebar facet property
		// _parentView.properties.appliedFacets = facetRestrictions;
		if (facet.type != "com.artesia.search.facet.FacetRefineByKeywordFieldRestriction") {

			OTDCPWSearchManager.clearSearchForKeyword(view.properties.nodeID);
			SelectionManager.clearSelection(view);
			var pageProperties;
			var routeName = "open";
			if (facetRestrictions.facets.length > 0) {
				if (view instanceof OTDCPWHomeView) {
					pageProperties = OTDCPWHomeView.getDefaultPageProperties();
					pageProperties.page = 0;
					view.callRoute(routeName, { 'appliedFacets': JSON.stringify(facetRestrictions), 'currentAccordion': 1, 'pageProperties': pageProperties });
				} else if (view instanceof OTDCBWHomeView) {
					pageProperties = OTDCBWHomeView.getDefaultPageProperties();
					pageProperties.page = 0;
					view.callRoute(routeName, { 'appliedFacets': JSON.stringify(facetRestrictions), 'currentAccordion': 1, 'pageProperties': pageProperties });
				}
			}

			if (view instanceof OTDCPWAssignToProductView && facetRestrictions.facets.length > 0) {
				//Reload assets and facets view				
				view.properties.appliedFacets = JSON.stringify(facetRestrictions);
				view.properties.pageProperties.page = '0';
				view.reload(true);
			}
		}
		else {
			return JSON.stringify(facetRestrictions);
		}
	};

	var _removeFacet = function (facet) {
		var view = otui.main.getChildView(OTDCBWHomeView) || otui.main.getChildView(OTDCPWHomeView) || (otui.main.getChildView(OTDCPWAssignToProdWrapper) && otui.main.getChildView(OTDCPWAssignToProdWrapper).getChildView(OTDCPWAssignToProductView));

		var facetRestrictions = JSON.parse(view.storedProperty("appliedFacets"));

		let removed = false;

		for (let i = 0; i < facetRestrictions.facets.length; i++) {
			let frFacet = facetRestrictions.facets[i];
			if (frFacet.field_id == facet.value?.fieldId || frFacet.field_id == facet.fieldID) {
				if (frFacet.type == "com.artesia.search.facet.FacetCascadingFieldRestriction") {
					if (frFacet.value_list[0])
						frFacet.value_list[0].component_list.splice(facet.value.component_list.length - 1);
					else
						frFacet.value_list = [];

					removed = true;

					if (frFacet.value_list[0] && frFacet.value_list[0].component_list.length == 0)
						frFacet.value_list = [];
				} else {
					for (let j = 0; j < frFacet.value_list.length; j++) {
						if (frFacet.type == "com.artesia.search.facet.FacetSimpleFieldRestriction" ||
							frFacet.type == "com.artesia.search.facet.FacetRefineByKeywordFieldRestriction") {
							if (frFacet.value_list[j] == facet.value?.displayValue || frFacet.value_list[j] == facet.value) {
								removed = true;
							}
						} else if (frFacet.type == "com.artesia.search.facet.FacetNumericRangeFieldRestriction"
							|| frFacet.type == "com.artesia.search.facet.FacetNumericIntervalFieldRestriction"
						) {
							if (frFacet.value_list[j].start_value == (facet.value.value?.start_value || facet.value.start_value)
								&& frFacet.value_list[j].end_value == (facet.value.value?.end_value ||  facet.value.end_value)) {
								removed = true;
							}
						} else if (frFacet.type == "com.artesia.search.facet.FacetDateIntervalFieldRestriction") {
							let frFacetValue = frFacet.value_list[j];
							if (frFacetValue.custom_range) {
								if (frFacetValue.fixed_start_date === (facet.value.value?.fixed_start_date || facet.value.fixed_start_date)
									&& frFacetValue.fixed_end_date === (facet.value.value?.fixed_end_date || facet.value.fixed_end_date)) {
									removed = true;
								}
							} else if (frFacetValue.interval_label === (facet.value.displayValue || facet.value.interval_label)) {
								removed = true;
							}
						} else if (frFacet.type == "com.artesia.search.facet.FacetDateRangeFieldRestriction") {
							let frFacetValue = frFacet.value_list[j];
							if (frFacetValue.start_date === (facet.value.value?.start_date || facet.value.start_date)
								&& frFacetValue.end_date === (facet.value.value?.end_date || facet.value.end_date)) {
								removed = true;
							}
						}
						if (removed) {
							frFacet.value_list.splice(j, 1);
							break;
						}
					}
				}
			}
			if (removed) {
				if (facetRestrictions.facets[i].value_list.length == 0)
					facetRestrictions.facets.splice(i, 1);
				break;
			}
		}
		//Remove clear icon from homeview
		$(".otdcpw-facets-clear-cont").remove();

		OTDCPWSearchManager.clearSearchForKeyword(view.properties.nodeID);
		SelectionManager.clearSelection(view);
		var pageProperties;
		var routeName = "open";
		if (view instanceof OTDCPWAssignToProductView) {
			//Reload assets and facets view				
			view.properties.appliedFacets = JSON.stringify(facetRestrictions);
			view.properties.pageProperties.page = '0';
			view.reload(true);
		} else {
			if (view instanceof OTDCPWHomeView) {
				pageProperties = OTDCPWHomeView.pwFolderParams.pageProperties;
			} else if (view instanceof OTDCBWHomeView) {
				pageProperties = OTDCBWHomeView.bwFolderParams.pageProperties;
			}
			pageProperties.sortField = "NAME";
			view.callRoute(routeName, { 'appliedFacets': JSON.stringify(facetRestrictions), 'currentAccordion': 0, 'pageProperties': pageProperties, 'filterTerm': "" });
		}
	};

	var _addRemoveFacetRestriction = function _addRemoveFacetRestriction(event) {
		if (event.currentTarget.checked == true) {
			_addFacet(this.data);
		}
		else if (event.currentTarget.checked == false) {
			_removeFacet(this.data);
		}
		else {
			var hasData = false;

			if (this.data.type == "com.artesia.search.facet.FacetDateRangeFieldRestriction") {
				var customDateRangeHolder = this.parentNode;
				var startDate = customDateRangeHolder.querySelector("#startDate").value;
				var endDate = customDateRangeHolder.querySelector("#endDate").value;

				if (startDate || endDate) {
					this.data.value.start_date = startDate ? startDate.toISOString() : null;
					this.data.value.end_date = endDate ? endDate.toISOString() : null;
					hasData = true;
				}

			}
			if (this.data.type == "com.artesia.search.facet.FacetDateIntervalFieldRestriction") {
				var customDateRangeHolder = this.parentNode;
				var startDate = customDateRangeHolder.querySelector("#startDate").value;
				var endDate = customDateRangeHolder.querySelector("#endDate").value;
				if (startDate || endDate) {
					this.data.value.fixed_start_date = startDate ? startDate.toISOString() : null;
					this.data.value.fixed_end_date = endDate ? endDate.toISOString() : null;
					hasData = true;
				}
				this.data.value.interval_label = otui.tr("{0} to {1}", otui.formatDate(startDate, otui.DateFormat.DATE), otui.formatDate(endDate, otui.DateFormat.DATE));
			}
			else if (this.data.type == "com.artesia.search.facet.FacetNumericRangeFieldRestriction" || this.data.type == "com.artesia.search.facet.FacetNumericIntervalFieldRestriction") {
				var customNumericRangeHolder = this.parentNode;
				this.data.value.start_value = $(customNumericRangeHolder).find("#startValue").val() || null;
				this.data.value.end_value = $(customNumericRangeHolder).find("#endValue").val() || null;

				if ((this.data.value.start_value && this.data.value.start_value.length > 0 && $.isNumeric(this.data.value.start_value))
					|| (this.data.value.end_value && this.data.value.end_value.length > 0 && $.isNumeric(this.data.value.end_value))) {
					hasData = true;
				}

				if (this.data.type == "com.artesia.search.facet.FacetNumericIntervalFieldRestriction") {
					var startValue = this.data.value.start_value;
					var endValue = this.data.value.end_value;

					if (otui.MetadataModelManager.isFilesizeField(this.data.fieldID)) {
						startValue = otui.FileUploadManager.getReadableFileSize(this.data.value.start_value);
						endValue = otui.FileUploadManager.getReadableFileSize(this.data.value.end_value);
					}

					this.data.value.interval_label = otui.tr("{0} to {1}", startValue, endValue);
				}
			}
			else if (this.data.type == "com.artesia.search.facet.FacetCascadingFieldRestriction") {
				hasData = true;
			}

			if (hasData) {
				_addFacet(this.data);
			}
		}

		if (otui.is(otui.modes.PHONE)) {
			var view = otui.JobsManager.getCurrentView(event);

			if (view) {
				// view.properties.needsUpdate = true;
				// view.properties.facetsRendered = false;
				otui.DialogUtils.cancelDialog(event.currentTarget);
				// view.blockContent({message: otui.tr('Applying filter...'), refresh: true});
				// view.onShow();
			}
		}
	};

	/**
	 * @class OTDCPWFacetsView
	 * @mixes Views.asView
	 * @mixes Views.asViewModule
	 * @mixes Views.asRoutable
	 * @mixes withContent
	 * @constructor
	 * @param {OTDCPWFacetsView.properties} props Properties for this view.
	 * @classdesc
	 * @namespace OTDCPWFacetsView
	 */
	exports.OTDCPWFacetsView = otui.define("OTDCPWFacetsView", ['withContent'],
		/** @lends OTDCPWFacetsView
		 *  @this OTDCPWFacetsView */
		function () {
			this.properties =
			{
				'name': "PwFILTERS",
				'title': otui.tr("Filters"),
				'type': 'folder',
				'savedFacetValueList': {},
				savedFacetList: []
			};

			// var _parentView;

			/**
			 * This function loads the content for a facets view and executes the supplied callback once loading has completed.
			 * @internal
			 */
			this._initContent = function initFacetsView(self, callback) {
				/*Changing to local variable as this is holding incorrect parentview and 
				its facets (because now facetsview is being used in both homeview and assign to product views)*/
				var _parentView = self.internalProperty("parentView");

				var content = self.getTemplate(otui.is(otui.modes.PHONE) ? "content_phone" : "content", undefined, $);

				if (otui.is(otui.modes.PHONE) && self.properties.parentViewID) {
					_parentView = otui.viewHolder.view(self.properties.parentViewID);
				}

				OTDCPWFacetsView.content = content;

				var appliedFacets = _parentView.properties.appliedFacets ? JSON.parse(_parentView.properties.appliedFacets).facets : [];

				if (otui.is(otui.modes.PHONE)) {
					var clearFiltersButton = content.find('.ot-remove-all');
					if (appliedFacets.length) {
						clearFiltersButton.css({ 'visibility': 'visible' });
						clearFiltersButton.click(function () {
							_removeAllFacets();
							otui.DialogUtils.cancelDialog(this);
						});
					}
					else {
						clearFiltersButton.css({ 'visibility': 'hidden' });
					}

				}

				if (appliedFacets.length && !this.properties.facetsRendered && _parentView.properties.savedSearchID === "none") {
					this.properties.facetsRendered = true;
					self.populateFacets(self, appliedFacets, callback);
				}
				else {
					content.find(".ot-facets-root .ot-facet").append("<span class='ot-facet-filters-none'>" + NOFILTERSMESSAGE + "</span>");
					callback(content);
				}
			};

			this.populateFacets = function (self, appliedFacets, callback) {
				var _parentView = self.internalProperty("parentView");
				let view = otui.main.getChildView(OTDCBWHomeView) || otui.main.getChildView(OTDCPWHomeView) || otui.main.getChildView(OTDCPWAssignToProdWrapper);
				if (OTDCUtilities.isPWContext()) {
					pageProperties = OTDCPWHomeView.getDefaultPageProperties();
				} else {
					pageProperties = OTDCBWHomeView.getDefaultPageProperties();
				}
				pageProperties.sortField = pageProperties.sortFieldforFacets;
				if (_parentView.constructor.name === "OTDCPWSidebarView")
					_parentView.storedProperty("pageProperties", pageProperties);

				var data;

				if (otui.is(otui.modes.PHONE)) {
					data = JSON.parse(JSON.stringify(_parentView.properties));
					data.pageProperties = pageProperties;
				}
				else
					data = _parentView.properties;

				data.folderFilterType = "direct";
				data.forFacetsOnly = true;

				var preferenceID = PageManager.getFieldPreferenceName();
				var extraFields = PageManager.getExtraFields();

				var properties = _parentView ? _parentView.properties : {};
				if (properties.searchID !== undefined && properties.searchID !== "none" && otui.is(otui.modes.PHONE)) {
					var newProperties = {};
					Object.assign(newProperties, properties);
					if (properties.collectionID) {
						newProperties.searchID = properties.collectionID + "_" + properties.searchConfigID + "_" + "undefined";
					}
					else {
						newProperties.searchID = properties.nodeID ? properties.nodeID + "_" + properties.searchConfigID + "_" + "undefined" : properties.sourceName;
					}
					properties = newProperties;
				}
				var facetConfigurationName = '';
				let defaultFacetName = '';
				if(OTDCUtilities.isPWContext()){
					defaultFacetName = OTDCUtilities.defaultPWFacetName;
					facetConfigurationName = (OTDCPWWorkspaceManager.config || {}).facetConfig || defaultFacetName;
					data.nodeID = OTDCPWWorkspaceManager.root_folder;
					properties.nodeID = OTDCPWWorkspaceManager.root_folder;
				} else {
					defaultFacetName = OTDCUtilities.defaultBWFacetName;
					facetConfigurationName = (OTDCBWWorkspaceManager.config || {}).facetConfig || defaultFacetName;
					data.nodeID = OTDCBWWorkspaceManager.root_folder;
					properties.nodeID = OTDCBWWorkspaceManager.root_folder;
				}

				//Check if currentfacetconfiguration holds the required info else update it
				if (OTDCPWFacetsManager.currentFacetConfiguration && OTDCPWFacetsManager.currentFacetConfiguration.name != facetConfigurationName) {
					OTDCPWFacetsManager.updateFacetConfiguration(facetConfigurationName, defaultFacetName);
				}
				if (!otui.is(otui.modes.PHONE) && properties && (properties.searchID || properties.nodeID) && (!properties.savedSearchID || properties.savedSearchID === "none")) {
					// Clearing the previously cached facets.
					if (properties.nodeID) {
						OTDCPWSearchManager.clearSearchForKeyword(properties.nodeID);
					}
				}

				//Check if facets not applied, if not make your own call
				var noFacets = !(JSON.parse(_parentView.properties.appliedFacets).facets.length);
				if (noFacets) {
					OTDCPWSearchManager.getFolderAssetsByKeyword("*", 0, PageManager.assetsPerPage, preferenceID, extraFields, data, function () { });
				}

				otui.services.pwfacetsResult.read(properties, facetConfigurationName, defaultFacetName, function (facets, pluginId) {
					/**
					 * Maintain a flag to prevent re-rendering of facets view which is leading
					 * to prevent showing data incorrectly under a facet.
					 */
					if (!!self.properties.isViewRendered) {
						delete self.properties.isViewRendered;
						//return;
					}
					//console.log(self.properties);

					var contentArea = self.contentArea();
					if (contentArea.find(".ot-facets-root .ot-facet").length == 0) contentArea = OTDCPWFacetsView.content;
					contentArea.find(".ot-facets-root .ot-facet").empty();

					self.buildFacets(contentArea, JSON.parse(JSON.stringify(facets)));

					if (facets.length == 0) {
						contentArea.find(".ot-facets-root .ot-facet").append("<span class='ot-facet-filters-none'>" + NOFILTERSMESSAGE + "</span>");
					}
					if (pluginId === "ARTESIA.PLUGIN.SEARCH.DATABASE" || self.properties.keywordSearchAllowed === false) {
						var keywordEl = contentArea.find('.ot-facets-text-filter-holder');
						if (keywordEl && keywordEl.length > 0) {
							keywordEl.hide();
						}
					}
					if (callback) callback(contentArea);
					self.properties.isViewRendered = true;
					//If facets are already applied, create clear button
					var alreadyExists = $(".otdcpw-facets-clear-cont")[0];
					//Get view
					var view = otui.main.getChildView(OTDCBWHomeView) || otui.main.getChildView(OTDCPWHomeView) || (otui.main.getChildView(OTDCPWAssignToProdWrapper) && otui.main.getChildView(OTDCPWAssignToProdWrapper).getChildView(OTDCPWAssignToProductView));
					if (!noFacets && !alreadyExists) {
						view && view.createClearButton();
					}
					//If no facets but button is visible, remove it
					if (noFacets && alreadyExists) {
						$(".otdcpw-facets-clear-cont").remove();
					}
				});
			};

			this.bind("show", function (event) {
				// var pwSidebarView = (this.internalProperties || {}).parentView;
				this.properties.needsUpdate = true;
				// var _parentView = pwSidebarView;
				//nitin
				//	_parentView.properties.appliedFacets = JSON.stringify({'facets': []});

				self = this;
				const urlParams = new URLSearchParams(window.location.search);
				const param1 = urlParams.get('p');

				let defaultFacetName = '';
				if (param1.startsWith('OTDCEAMDashboard')) {
					defaultFacetName = OTDCUtilities.defaultBWFacetName;
					facetConfigurationName = (OTDCBWWorkspaceManager.config || {}).facetConfig || defaultFacetName;
				} else {
					defaultFacetName = OTDCUtilities.defaultPWFacetName;
					facetConfigurationName = (OTDCPWWorkspaceManager.config || {}).facetConfig || defaultFacetName;
				}

				OTDCPWFacetsManager.readFacets(facetConfigurationName, defaultFacetName, function () {
					self.onShow();
				});
			});

			this.onShow = function () {
				var _parentView = this.internalProperty("parentView");
				var show = this.properties.needsUpdate && !this.properties.facetsRendered;
				if (otui.is(otui.modes.PHONE))
					show = this.properties.needsUpdate && _parentView && !this.properties.facetsRendered;

				if (show) {
					this.properties.needsUpdate = false;
					this.properties.facetsRendered = true;
					this.updateFacets(JSON.parse(_parentView.properties.appliedFacets).facets);
				}
			};

			this.isFacetApplied = function (facet) {
				var _parentView = this.internalProperty("parentView");
				var appliedFacets = _parentView.properties.appliedFacets ? JSON.parse(_parentView.properties.appliedFacets) : null;
				var found = false;

				if (appliedFacets)
					for (var i = 0; i < appliedFacets.facets.length; i++) {
						if (appliedFacets.facets[i].field_id == facet.fieldID) {
							for (var j = 0; j < appliedFacets.facets[i].value_list.length; j++) {
								if (appliedFacets.facets[i].type == "com.artesia.search.facet.FacetSimpleFieldRestriction") {
									if (appliedFacets.facets[i].value_list[j] == facet.value) {
										found = true;
									}
								}
								else if (appliedFacets.facets[i].type == "com.artesia.search.facet.FacetNumericRangeFieldRestriction") {
									if (appliedFacets.facets[i].value_list[j].end_value == facet.value.end_value) {
										found = true;
									}
								}
								else if (appliedFacets.facets[i].type == "com.artesia.search.facet.FacetNumericIntervalFieldRestriction") {
									if (appliedFacets.facets[i].value_list[j].interval_label == facet.value.interval_label) {
										found = true;
									}
								}
								else if (appliedFacets.facets[i].type == "com.artesia.search.facet.FacetDateIntervalFieldRestriction") {
									if (appliedFacets.facets[i].value_list[j].interval_label == facet.value.interval_label) {
										found = true;
									}
								}
								else if (appliedFacets.facets[i].type == "com.artesia.search.facet.FacetDateRangeFieldRestriction") {
									if (appliedFacets.facets[i].value_list[j].end_date == facet.value.end_date) {
										found = true;
									}
								}

								if (found) {
									break;
								}
							}
						}
					}

				return found;
			};

			/**
			 * Returns true if the facet value is applied.
			 *
			 */
			this.hasAppliedFacets = function (facet) {
				var _parentView = this.internalProperty("parentView");
				var appliedFacets = _parentView.properties.appliedFacets ? JSON.parse(_parentView.properties.appliedFacets) : null;
				var found = false;

				if (appliedFacets) {
					for (var i = 0; (i < appliedFacets.facets.length) && !found; i++) {
						found = (appliedFacets.facets[i].field_id === facet._facet_field_request.field_id);
					}
				}

				return found;

			};


			this.getAppliedFacetValues = function (field_id) {
				var _parentView = this.internalProperty("parentView");
				var appliedFacets = _parentView.properties.appliedFacets ? JSON.parse(_parentView.properties.appliedFacets) : null;

				if (appliedFacets) {
					for (var i = 0; i < appliedFacets.facets.length; i++) {
						if (appliedFacets.facets[i].field_id === field_id) {
							for (var j = 0; j < appliedFacets.facets[i].value_list.length; j++) {
								if (appliedFacets.facets[i].value_list[j].custom_range) {
									return appliedFacets.facets[i].value_list[j];
								}
							}
						}
					}
				}

				return null;
			};

			this.buildFacets = function (content, facets) {
				var facetsBase = this.getTemplate("FacetsBase", undefined, $);
				var facetsBaseClone = null;
				var facetTemplateMaster = null;
				var facetTemplateClone = null;
				var elementsToRemoveString = null;
				var elementsToRemove = null;
				var removedElements;
				var insertAnchor = null;
				var input = null;
				var data = null;
				var facetTitle = "";
				var facetsToExpand = [];
				var defaultFacetDisplayCount = OTDCPWFacetsManager.currentFacetConfiguration.default_facets_displayed;

				facets = facets && Array.isArray(facets) ? facets : [];

				this.properties.savedFacetList = facets.slice(0);

				this.renderFacetSet(content);


			};

			this.renderFacetSet = function (content) {

				var defaultFacetDisplayCount = OTDCPWFacetsManager.currentFacetConfiguration.default_facets_displayed;
				var facets = [];
				//content = this.contentArea();
				if (this.properties.savedFacetList.length > defaultFacetDisplayCount) {
					facets = this.properties.savedFacetList.splice(0, defaultFacetDisplayCount);
				}
				else {
					facets = this.properties.savedFacetList;
					this.properties.savedFacetList = [];
				}

				for (var i = 0; i < facets.length; i++) {
					this.renderFacet(facets[i], content);
				}

				var contentArea = this.contentArea();
				if (contentArea.find('.ot-contentblock').length)
					this.unblockContent();

			};



			this.renderFacet = function (facet, content) {
				var facetsBase = this.getTemplate("FacetsBase", undefined, $);
				var facetsBaseClone = null;
				var facetTemplateMaster = null;
				var elementsToRemoveString = null;
				var elementsToRemove = null;
				var removedElements;
				var insertAnchor = null;
				var input = null;
				var data = null;
				var facetTitle = "";
				var facetsToExpand = [];
				var isFileSizeFacet = otui.MetadataModelManager.isFilesizeField(facet._facet_field_request.field_id) || false;
				var appliedFacetValues = otui.is(otui.modes.PHONE) && this.getAppliedFacetValues(facet["_facet_field_request"].field_id);

				facetsBaseClone = facetsBase.clone();

				facetTitle = otui.MetadataModelManager.getDisplayName(facet._facet_field_request.field_id) || facet._facet_field_request.field_id;

				if (facet._parent_path && facet._parent_path.component_list)
					facetTitle += ": " + facet._parent_path.component_list.join("/");

				facetsBaseClone.find(".ot-facets-base-title").text(facetTitle);

				facetsBaseClone.find(".ot-facets-base-title").on("mouseenter", function (event) {
					var $this = $(this);

					if (this.offsetWidth < this.scrollWidth && !$this.attr('title')) {
						$this.attr('title', $this.text());
					}
				});

				facetTemplateMaster = this.getTemplate("facet-type", facet, $);
				elementsToRemoveString = facetTemplateMaster.attr("ot-norepeat");
				elementsToRemove = elementsToRemoveString ? elementsToRemoveString.split(",") : [];

				removedElements = [];

				for (var k = 0; k < elementsToRemove.length; k++) {
					removedElements = facetTemplateMaster.find("#" + elementsToRemove[k]).remove();
				}

				var displayAllFacetValues = !OTDCPWFacetsManager.currentFacetConfiguration.default_facet_values_displayed;
				this.renderFacetValue(facet, facetsBaseClone, facetTemplateMaster, displayAllFacetValues);

				for (var l = 0; l < removedElements.length; l++) {
					insertAnchor = $(removedElements[l]).attr("ot-insert");

					data = {};
					data.fieldID = facet["_facet_field_request"].field_id;
					data.multiSelect = facet["_facet_field_request"].multi_select;


					if (facet.type == "com.artesia.search.facet.FacetDateRangeFieldResponse") {
						if (appliedFacetValues) {
							$(removedElements[l]).find("#startDate").val(appliedFacetValues.start_date ? new Date(appliedFacetValues.start_date) : undefined);
							$(removedElements[l]).find("#endDate").val(appliedFacetValues.end_date ? new Date(appliedFacetValues.end_date) : undefined);
						}

						data.type = "com.artesia.search.facet.FacetDateRangeFieldRestriction";
						data.value = { "custom_range": true, "end_date": undefined, "start_date": undefined };
					}
					else if (facet.type == "com.artesia.search.facet.FacetDateIntervalFieldResponse") {
						if (appliedFacetValues) {
							$(removedElements[l]).find("#startDate").val(appliedFacetValues.fixed_start_date ? new Date(appliedFacetValues.fixed_start_date) : undefined);
							$(removedElements[l]).find("#endDate").val(appliedFacetValues.fixed_end_date ? new Date(appliedFacetValues.fixed_end_date) : undefined);
						}

						data.type = "com.artesia.search.facet.FacetDateIntervalFieldRestriction";
						data.value = { "custom_range": true, "fixed_end_date": undefined, "fixed_start_date": undefined };
					}
					else if (facet.type == "com.artesia.search.facet.FacetNumericRangeFieldResponse") {
						if (appliedFacetValues) {
							$(removedElements[l]).find("#startValue").val(appliedFacetValues.start_value);
							$(removedElements[l]).find("#endValue").val(appliedFacetValues.end_value);
						}

						data.type = "com.artesia.search.facet.FacetNumericRangeFieldRestriction";
						data.value = { "custom_range": true, "end_value": undefined, "start_value": undefined };
					}
					else if (facet.type == "com.artesia.search.facet.FacetNumericIntervalFieldResponse") {
						if (appliedFacetValues) {
							$(removedElements[l]).find("#startValue").val(appliedFacetValues.start_value);
							$(removedElements[l]).find("#endValue").val(appliedFacetValues.end_value);
						}

						data.type = "com.artesia.search.facet.FacetNumericIntervalFieldRestriction";
						data.value = { "custom_range": true, "end_inclusive": true, "start_inclusive": true, "end_value": undefined, "start_value": undefined };
					}

					input = $(removedElements[l]).find(".ot-button");
					input.click(_addRemoveFacetRestriction);
					input[0].data = data;

					facetsBaseClone.find(insertAnchor).append(removedElements[l]);
				}

				if (isFileSizeFacet) {
					otui.tr("From (in bytes)");
					otui.tr("To (in bytes)");
					var fileSizeLabels = facetsBaseClone.find(".ot-facet-filesize-label");
					fileSizeLabels[0].setAttribute("ot-token", "From (in bytes)");
					fileSizeLabels[1].setAttribute("ot-token", "To (in bytes)");
				}

				content.find(".ot-facets-root .ot-facet").append(facetsBaseClone);
			}

			this.renderFacetValue = function (facet, facetsBaseClone, facetValueTemplate, showAll) {
				var isFileSizeFacet = false, facetTemplateClone;
				var facetValueList = facet["_facet_value_list"];
				var hasAppliedFacet = this.hasAppliedFacets(facet);
				var j = 0;
				var processNextIteration = true;
				var facetValueNumber = facetValueList.length;
				var valueOffset = parseInt(facet["_value_offset"] || 0);
				for (; j < facetValueNumber && processNextIteration; j++) {
					facetTemplateClone = facetValueTemplate.clone();

					data = {};
					data.fieldID = facet["_facet_field_request"].field_id;
					data.multiSelect = facet["_facet_field_request"].multi_select;

					if (facet.type == "com.artesia.search.facet.FacetSimpleFieldResponse") {
						data.type = "com.artesia.search.facet.FacetSimpleFieldRestriction";
						var textValue = otui.TranslationManager.getTranslation(facet["_facet_value_list"][j].value);
						facetTemplateClone.find('.ot-facet-simple-label').text(textValue || otui.tr("undefined"));
						facetTemplateClone.find('.ot-facet-simple-total').text(facet["_facet_value_list"][j].asset_count);
						data.value = facet["_facet_value_list"][j].value || null;
					}
					else if (facet.type == "com.artesia.search.facet.FacetNumericRangeFieldResponse") {
						data.type = "com.artesia.search.facet.FacetNumericRangeFieldRestriction";
						var startValue = facet["_facet_value_list"][j].numeric_range.start_value;
						var endValue = facet["_facet_value_list"][j].numeric_range.end_value;
						var textValue = undefined;

						isFileSizeFacet = otui.MetadataModelManager.isFilesizeField(facet._facet_field_request.field_id);
						startValue = !isNaN(startValue) && isFileSizeFacet ? otui.FileUploadManager.getReadableFileSize(startValue) : startValue;

						if (j === 0 && isFileSizeFacet) {
							textValue = !isNaN(endValue) && isFileSizeFacet ? otui.FileUploadManager.getReadableFileSize(endValue) : startValue;
							textValue = " < " + textValue;
						}
						else if (!startValue && startValue != 0) {
							textValue = " < " + endValue;
						}

						if (!isNaN(endValue) && isFileSizeFacet) {
							endValue = otui.FileUploadManager.getReadableFileSize(endValue);
						}
						else if (!endValue && endValue != 0) {
							textValue = " > " + startValue;
						}

						if (!textValue) {
							textValue = startValue + " - " + endValue;
						}

						facetTemplateClone.find('.ot-facet-simple-label').text(textValue);
						facetTemplateClone.find('.ot-facet-simple-total').text(facet["_facet_value_list"][j].asset_count);
						data.value = facet["_facet_value_list"][j].numeric_range;
					}
					else if (facet.type == "com.artesia.search.facet.FacetNumericIntervalFieldResponse") {
						data.type = "com.artesia.search.facet.FacetNumericIntervalFieldRestriction";
						var textValue = otui.TranslationManager.getTranslation(facet["_facet_value_list"][j].numeric_interval.interval_label);
						facet["_facet_value_list"][j].numeric_interval.interval_label = textValue;

						isFileSizeFacet = otui.MetadataModelManager.isFilesizeField(facet._facet_field_request.field_id);

						facetTemplateClone.find('.ot-facet-simple-label').text(textValue);
						facetTemplateClone.find('.ot-facet-simple-total').text(facet["_facet_value_list"][j].asset_count);
						data.value = facet["_facet_value_list"][j].numeric_interval;
					}
					else if (facet.type == "com.artesia.search.facet.FacetDateIntervalFieldResponse") {
						data.type = "com.artesia.search.facet.FacetDateIntervalFieldRestriction";
						var textValue = otui.TranslationManager.getTranslation(facet["_facet_value_list"][j].date_interval.interval_label);

						if (facet["_facet_value_list"][j].token_expansion_list && facet["_facet_value_list"][j].token_expansion_list.length) {
							var index = 0;
							textValue = textValue.replace(/%%YEAR((\-|\+)\d+)*%%/g, function () { return facet["_facet_value_list"][j].token_expansion_list[index++]; });
							facet["_facet_value_list"][j].date_interval.interval_label = textValue;
						}

						facetTemplateClone.find('.ot-facet-simple-label').text(textValue);
						facetTemplateClone.find('.ot-facet-simple-total').text(facet["_facet_value_list"][j].asset_count);
						data.value = facet["_facet_value_list"][j].date_interval;
					}
					else if (facet.type == "com.artesia.search.facet.FacetDateRangeFieldResponse") {
						data.type = "com.artesia.search.facet.FacetDateRangeFieldRestriction";
						facetTemplateClone.find('.ot-facet-simple-label').text((facet["_facet_value_list"][j].date_range.start_date ? otui.formatDate(new Date(facet["_facet_value_list"][j].date_range.start_date), otui.DateFormat.DATE) : otui.tr("before")) + " - " + (facet["_facet_value_list"][j].date_range.end_date ? otui.formatDate(new Date(facet["_facet_value_list"][j].date_range.end_date), otui.DateFormat.DATE) : otui.tr("after")));
						facetTemplateClone.find('.ot-facet-simple-total').text(facet["_facet_value_list"][j].asset_count);
						data.value = facet["_facet_value_list"][j].date_range;
					}
					else if (facet.type == "com.artesia.search.facet.FacetCascadingFieldResponse") {
						data.type = "com.artesia.search.facet.FacetCascadingFieldRestriction";
						var pathCompList = ((facet["_facet_value_list"][j].path || {}).component_list || []);
						facetTemplateClone.find('.ot-facet-cascading-label').text(pathCompList[pathCompList.length - 1] || "undefined");
						facetTemplateClone.find('.ot-facet-simple-total').text(facet["_facet_value_list"][j].asset_count);
						data.value = facet["_facet_value_list"][j].path;
					}

					facetTemplateClone.find('.ot-facet-simple-label').on("mouseenter", function (event) {
						var $this = $(this);

						if (this.offsetWidth < this.scrollWidth && !$this.attr('title')) {
							$this.attr('title', $this.text());
						}
					});

					facetTemplateClone.find('.ot-facet-cascading-label').on("mouseenter", function (event) {
						var $this = $(this);

						if (this.offsetWidth < this.scrollWidth && !$this.attr('title')) {
							$this.attr('title', $this.text());
						}
					});

					if (facet.type == "com.artesia.search.facet.FacetSimpleFieldResponse" || facet.type == "com.artesia.search.facet.FacetNumericRangeFieldResponse" || facet.type == "com.artesia.search.facet.FacetDateRangeFieldResponse" || facet.type === "com.artesia.search.facet.FacetNumericIntervalFieldResponse" || facet.type === "com.artesia.search.facet.FacetDateIntervalFieldResponse") {
						input = facetTemplateClone.find('.ot-facet-simple-checkbox');

						input.attr("id", data.fieldID + "_" + data.value + "_" + (j + valueOffset));

						var checkboxLabel = facetTemplateClone.find(".ot-facet-simple-label");
						checkboxLabel.attr("for", data.fieldID + "_" + data.value + "_" + (j + valueOffset));
						checkboxLabel.on("mouseenter", function (event) {
							var $this = $(this);

							if (this.offsetWidth < this.scrollWidth && !$this.attr('title')) {
								$this.attr('title', $this.text());
							}
						});

						input.attr("type", data.multiSelect ? "checkbox" : "radio");
						input.addClass(data.multiSelect ? "ot-checkbox" : "ot-radio");

						input.change(_addRemoveFacetRestriction);

						input[0].data = data;

						var isFacetApplied = this.isFacetApplied(data);
						input.prop("checked", isFacetApplied);
					}
					else if (facet.type == "com.artesia.search.facet.FacetCascadingFieldResponse") {
						input = facetTemplateClone.find('.ot-facet-cascading-label');

						input[0].data = data;

						input.click(_addRemoveFacetRestriction);
					}

					facetsBaseClone.find(".ot-facets-base-content").append(facetTemplateClone);
					processNextIteration = hasAppliedFacet || (j + 1 < OTDCPWFacetsManager.currentFacetConfiguration.default_facet_values_displayed) || showAll;

					if (!processNextIteration) {
						facetValueList.splice(0, j + 1);
						facet["_facet_value_list"] = facetValueList;
						facet["_value_offset"] = valueOffset + j + 1;
						facetsBaseClone.attr("ot-field-id", facet._facet_field_request.field_id);
						this.saveFacetValueList(facet);
					}

				}

				if (j < facetValueNumber) {
					facetsBaseClone.find(".ot-facets-base-more").attr("style", "display: block;");
					var clonedEl = facetTemplateClone.clone();
					document.body.appendChild(clonedEl[0]);
					facetsBaseClone.find(".ot-facets-base-content").attr("style", "height: " + (clonedEl.outerHeight(true) * OTDCPWFacetsManager.currentFacetConfiguration.default_facet_values_displayed - parseInt(clonedEl.css("margin-top"))) + "px;");
					document.body.removeChild(clonedEl[0]);
				}
				else if (j === facetValueNumber && j > OTDCPWFacetsManager.currentFacetConfiguration.default_facet_values_displayed && !showAll) {
					var clonedEl = facetTemplateClone.clone();
					document.body.appendChild(clonedEl[0]);

					facetsBaseClone.find(".ot-facets-base-content").attr("ot-facets-base-content-height", (clonedEl.outerHeight(true) * OTDCPWFacetsManager.currentFacetConfiguration.default_facet_values_displayed - parseInt(clonedEl.css("margin-top"))) + "px");
					document.body.removeChild(clonedEl[0]);
					facetsBaseClone.find(".ot-facets-base-content").css("height", "");
					facetsBaseClone.find(".ot-facets-base-more").text(otui.tr("Show less..."));
					facetsBaseClone.find(".ot-facets-base-more").attr("ot-facet-showing", "true");
					facetsBaseClone.find(".ot-facets-base-more").attr("style", "display: block;");
				}
			}

			this.updateFacets = function (facets) {
				var self = this;
				this.populateFacets(self, facets);
			};
			this.saveFacetValueList = function (facet) {
				this.properties.savedFacetValueList[facet._facet_field_request.field_id] = facet;
			};
			this.getSavedFacetValueList = function (fieldId) {
				return this.properties.savedFacetValueList[fieldId];
			};
		});

	OTDCPWFacetsView.content = undefined;

	OTDCPWFacetsView.hideFacets = function () {
		var facetsRoot = OTDCPWFacetsView.content.find(".ot-facets-root");
		var facets = facetsRoot.children();

		if (facets.length > OTDCPWFacetsManager.currentFacetConfiguration.default_facets_displayed) {
			var iterationCount = 1;
			var lastFacetSpan = null;
			var isMoreFacets = false;
			lastFacetSpan = $(facets[facets.length - 1]).find("ot-layout > span[style]");
			if (lastFacetSpan.length > 0) {
				if (arguments != null && arguments.length > 0) {
					iterationCount = arguments[0];
				}
				// Added this condition to verify that left side facet layout is loaded or not.
				if (lastFacetSpan[0].attributes["style"].value.indexOf("calc") == -1 && iterationCount < 20) {
					iterationCount = iterationCount + 1;
					setTimeout("OTDCPWFacetsView.hideFacets(" + iterationCount + ")", 100);
					return;
				}
			}
			for (var i = 0; i < facets.length; i++) {
				if (i >= OTDCPWFacetsManager.currentFacetConfiguration.default_facets_displayed) {
					if (!isMoreFacets) isMoreFacets = true;
					$(facets[i]).hide();
				}
				else if (i == (OTDCPWFacetsManager.currentFacetConfiguration.default_facets_displayed - 1)) {
					$(facets[i]).addClass("ot-facets-base-holder-bottom-border");
				}
			}

		}
	};


	OTDCPWFacetsView.expandFacetValueList = function (event, facetContainer) {
		var fieldId = facetContainer.getAttribute("ot-field-id");
		//Tweaking the logic as currentView is not pointing to facetsview in case of assign to product view
		var view = otui.main.getChildView(OTDCBWHomeView) || otui.main.getChildView(OTDCPWHomeView) || otui.main.getChildView(OTDCPWAssignToProdWrapper);
		if (view instanceof OTDCPWAssignToProdWrapper) {
			var currentView = otui.main.getChildView(OTDCPWAssignToProdWrapper).getChildView(OTDCPWSidebarView).getChildView(OTDCPWFacetsView);
		} else {
			var currentView = otui.Views.containing(facetContainer);
		}

		var baseHeight = 0;


		if ($(facetContainer).find(".ot-facets-base-more").attr("ot-facet-showing") == "false") {
			var facet = currentView.getSavedFacetValueList(fieldId);

			if (!facetContainer.hasAttribute("ot-loaded-all-vals") && facet) {
				var facetTemplateMaster = currentView.getTemplate("facet-type", facet, $);
				var elementsToRemoveString = facetTemplateMaster.attr("ot-norepeat");
				var elementsToRemove = elementsToRemoveString ? elementsToRemoveString.split(",") : [];


				for (var k = 0; k < elementsToRemove.length; k++) {
					facetTemplateMaster.find("#" + elementsToRemove[k]).remove();
				}

				currentView.renderFacetValue(facet, $(facetContainer), facetTemplateMaster, true);
			}

			$(facetContainer).attr("ot-loaded-all-vals", "true");
			baseHeight = $(facetContainer).find(".ot-facets-base-content").attr("ot-facets-base-content-height");
			if (!baseHeight) {
				baseHeight = $(facetContainer).find(".ot-facets-base-content").height() + "px";
			}
			$(facetContainer).find(".ot-facets-base-content").attr("ot-facets-base-content-height", baseHeight);
			$(facetContainer).find(".ot-facets-base-content").css("height", "");
			$(facetContainer).find(".ot-facets-base-more").text(otui.tr("Show less..."));
			$(facetContainer).find(".ot-facets-base-more").attr("ot-facet-showing", "true");
		}
		else {
			$(facetContainer).find(".ot-facets-base-content").css("height", $(facetContainer).find(".ot-facets-base-content").attr("ot-facets-base-content-height"));
			$(facetContainer).find(".ot-facets-base-more").text(otui.tr("Show more..."));
			$(facetContainer).find(".ot-facets-base-more").attr("ot-facet-showing", "false");
		}

	};

	OTDCPWFacetsView.toggleFacet = function (event) {
		var facetContainer = event.currentTarget.parentNode;
		if ($(facetContainer).find(".ot-facets-base-content").css("display") != "none") {
			$(facetContainer).find(".ot-facets-base-content").addClass("hide");
			$(facetContainer).find(".ot-facets-base-static").addClass("hide");
			$(facetContainer).find(".ot-facets-base-more").addClass("hide");
			$(facetContainer).addClass("closed");
		}
		else {
			$(facetContainer).find(".ot-facets-base-content").removeClass("hide");
			$(facetContainer).find(".ot-facets-base-more").removeClass("hide");
			$(facetContainer).find(".ot-facets-base-static").removeClass("hide");
			$(facetContainer).removeClass("closed");
		}
	};

	OTDCPWFacetsView.handleKeyUp = function (event) {
		var input = $(event.currentTarget);
		if (event.keyCode == 13) {
			OTDCPWFacetsView.performKeywordSearch();
		}

		if (input.val() == "") {
			$('.ot-facets-search-clear').hide();
		}
		else {
			if (!input.hasClass("focused"))
				input.addClass("focused");
			$('.ot-facets-search-clear').show();
		}
	};

	OTDCPWFacetsView._removeFacet = function(view, eventDetail){
		let facetRestrictions = JSON.parse(view.storedProperty("appliedFacets"));
		let modifiedFacets = eventDetail.appliedFacets;
		let pageProperties;
		var routeName = "open";
		if (view instanceof OTDCPWHomeView) {
			pageProperties = OTDCPWHomeView.pwFolderParams.pageProperties;
		} else if (view instanceof OTDCBWHomeView) {
			pageProperties = OTDCBWHomeView.bwFolderParams.pageProperties;
		}
		view.callRoute(routeName, { 'appliedFacets': JSON.stringify({'facets':modifiedFacets}), 'currentAccordion': 0, 'pageProperties': pageProperties, 'filterTerm': "" });
	};

	OTDCPWFacetsView.performKeywordSearch = function (view, eventDetail) {
		if(!view) {
			view = otui.main.getChildView(OTDCBWHomeView) || otui.main.getChildView(OTDCPWHomeView) || (otui.main.getChildView(OTDCPWAssignToProdWrapper) && otui.main.getChildView(OTDCPWAssignToProdWrapper).getChildView(OTDCPWAssignToProductView));
		}

		let inputVal = [];

		let appliedFacets = JSON.parse(view.storedProperty("appliedFacets"));
		let searchFacet = appliedFacets.facets.find(facet => facet.field_id === "com.artesia.search.facet.FacetRefineByKeywordFieldRestriction");

		if(searchFacet){
			inputVal = searchFacet.value_list;
		}
		
		let modifiedSearchFacet = eventDetail.appliedFacets.find(facet => facet.field_id === "com.artesia.search.facet.FacetRefineByKeywordFieldRestriction");
		
		inputVal.push(modifiedSearchFacet.value_list[modifiedSearchFacet.value_list.length -1]);


		if (view && inputVal.length > 0 && view.properties.searchID != OTDCPWSearchManager.SHOW_SEARCH_TOKEN) {

			let searchTerm = view.properties.searchID || view.properties.filterTerm;
			let properties;

			let routeName = "open";
			if (view instanceof OTDCPWAssignToProductView) {
				//Reload assets and facets view				
				// view.properties.appliedFacets = JSON.stringify(facetRestrictions);
				view.properties.pageProperties = OTDCPWHomeView.getDefaultPageProperties();
				view.properties.pageProperties.page = '0';
				view.properties.appliedFacets = _addFacet({ "type": "com.artesia.search.facet.FacetRefineByKeywordFieldRestriction", "fieldID": "com.artesia.search.facet.FacetRefineByKeywordFieldRestriction", "value": inputVal });
				view.reload(true);
			} else {
				OTDCPWSearchManager.clearSearchForKeyword(view.properties.folderID);
				properties = { 'filterTerm': searchTerm, 'currentAccordion': 1, 'appliedFacets': _addFacet({ "type": "com.artesia.search.facet.FacetRefineByKeywordFieldRestriction", "fieldID": "com.artesia.search.facet.FacetRefineByKeywordFieldRestriction", "value": inputVal }) };
				if (OTDCUtilities.isPWContext()) {
					properties.pageProperties = OTDCPWHomeView.getDefaultPageProperties();
				} else {
					properties.pageProperties = OTDCBWHomeView.getDefaultPageProperties();
				}
				properties.pageProperties.page = 0;
				view.callRoute(routeName, properties);
			}

		}
	};


	OTDCPWFacetsView.handleClearFilters = function handleClearFilters() {

		//Remove clear icon from the view and route the view
		$(".otdcpw-facets-clear-cont").remove();
		var view = otui.main.getChildView(OTDCBWHomeView) || otui.main.getChildView(OTDCPWHomeView) || (otui.main.getChildView(OTDCPWAssignToProdWrapper) && otui.main.getChildView(OTDCPWAssignToProdWrapper).getChildView(OTDCPWAssignToProductView));
		var facetRestrictions = JSON.parse(view.storedProperty("appliedFacets"));
		facetRestrictions.facets = [];
		OTDCPWSearchManager.clearSearchForKeyword(view.properties.nodeID);
		SelectionManager.clearSelection(view);
		var pageProperties;
		var routeName = "open";
		if (view instanceof OTDCPWAssignToProductView) {
			//Reload assets and facets view				
			view.properties.appliedFacets = JSON.stringify(facetRestrictions);
			view.properties.pageProperties.page = '0';
			view.reload(true);
		} else {
			if (OTDCUtilities.isPWContext()) {
				pageProperties = OTDCPWHomeView.pwFolderParams.pageProperties;
			} else {
				pageProperties = OTDCBWHomeView.bwFolderParams.pageProperties;
			}
			pageProperties.sortField = "NAME";
			view.callRoute(routeName, { 'appliedFacets': JSON.stringify(facetRestrictions), 'currentAccordion': 0, 'pageProperties': pageProperties, 'filterTerm': "" });
		}
	}
})(window);
