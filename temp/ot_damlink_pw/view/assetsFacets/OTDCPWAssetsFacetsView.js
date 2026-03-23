(function (exports) {
	let NOFILTERSMESSAGE = otui.tr("No filters are available for your current search results...");
	let defaultFacetName = 'OTDC.PW.ASSET.FACET.CONFIG';

	let _removeAllFacets = function () {
		let assetView;
        let view = otui.main.getChildView(OTDCPWDetailView) || otui.main.getChildView(OTDCPWDetailEditView);
        const { assetView: av } = getAssetAndSidebarViews(view, assetView);
        assetView = av;

        OTDCPWAssetSearchManager.clearSearchForKeyword(view.properties.nodeID);

        SelectionManager.clearSelection(view);

        if (view instanceof OTDCPWDetailView || view instanceof OTDCPWDetailEditView) {
            assetView.properties.appliedFacets = JSON.stringify({ 'facets': [] });
            assetView.properties.pageProperties.page = '0';
            assetView.reload(true);
        }
	};

	/**
	 * This method is invoked when user selects any facet in FacetsView
	 * @param {*} facet 
	 * @returns 
	 */
	let _addFacet = function (facet) {
		let view = otui.main.getChildView(OTDCPWDetailView) || otui.main.getChildView(OTDCPWDetailEditView);

		const { assetView, otdcpwSidebarView } = getAssetAndSidebarViews(view);

		let facetRestrictions = OTDCUtilities.addFacetToFacetRestrictions(assetView, facet);

		if (facet.type != "com.artesia.search.facet.FacetRefineByKeywordFieldRestriction") {
			OTDCPWAssetSearchManager.clearSearchForKeyword(view.properties.nodeID);
			if (facetRestrictions.facets.length > 0) {
				if ((view instanceof OTDCPWDetailView || view instanceof OTDCPWDetailEditView)) {
					assetView.properties.appliedFacets = JSON.stringify(facetRestrictions);
					assetView.properties.pageProperties.page = '0';
				}
			}
		}
		else {
			return JSON.stringify(facetRestrictions);
		}
	};

	/**
	 * function to remove/unselect facet 
	 * @param {*} event facet is received in event in case on unselection from facetsView. or click event when clicking X icon in chicklet.
	 */
	let _removeFacet = function (removedFacet) {
		let view = otui.main.getChildView(OTDCPWDetailView) || otui.main.getChildView(OTDCPWDetailEditView);
		const { assetView, otdcpwSidebarView } = getAssetAndSidebarViews(view);

		let facetRestrictions = JSON.parse(assetView.properties.appliedFacets);

		let removed = false;
		for(let i=0; i < facetRestrictions.facets.length; i++) {
			let selectedFacet = facetRestrictions.facets[i];
			if(selectedFacet.field_id === removedFacet.fieldID) {
				let index = -1;
				if(selectedFacet.type == "com.artesia.search.facet.FacetSimpleFieldRestriction") {
					index = selectedFacet.value_list.indexOf(removedFacet.value);
				} else if (selectedFacet.type == "com.artesia.search.facet.FacetNumericRangeFieldRestriction") {
					index = selectedFacet.value_list.findIndex(facet => facet.start_value === removedFacet.value.start_value && facet.end_value === removedFacet.value.end_value);
				} else if (selectedFacet.type == "com.artesia.search.facet.FacetDateIntervalFieldRestriction"
					|| selectedFacet.type == "com.artesia.search.facet.FacetDateRangeFieldRestriction"
					|| selectedFacet.type == "com.artesia.search.facet.FacetNumericIntervalFieldRestriction"
				) {
					index = selectedFacet.value_list.findIndex(facet => facet.interval_label === removedFacet.value.interval_label);
				}

				if (index > -1) {
					selectedFacet.value_list.splice(index, 1);
					if (selectedFacet.value_list.length == 0) {
						facetRestrictions.facets.splice(i, 1);
					} else {
						facetRestrictions.facets[i] = selectedFacet;
					}
					removed = true;
				}
			}
		}

		if(removed && (assetView instanceof OTDCPWAssetsView || assetView instanceof OTDCPWAssetsEditView)) {
			OTDCPWAssetSearchManager.clearSearchForKeyword(view.properties.nodeID);
			assetView.properties.pageProperties.sortField = "NAME";
			assetView.properties.appliedFacets = JSON.stringify(facetRestrictions);
			assetView.properties.pageProperties.page = '0';
		}
	};

	let _addRemoveFacetRestriction = function _addRemoveFacetRestriction(event) {
		if (event.currentTarget.checked == true) {
			_addFacet(this.data);
			if (this.data.multiSelect == false) {
				let view = otui.main.getChildView(OTDCPWDetailView) || otui.main.getChildView(OTDCPWDetailEditView);
				const { assetView, otdcpwSidebarView } = getAssetAndSidebarViews(view);

				if (otdcpwSidebarView && assetView) {
					updateSidebarFacets(assetView, otdcpwSidebarView);
				}
			}
		}
		else if (event.currentTarget.checked == false) {
			_removeFacet(this.data);
		}
		else {
			let hasData = false;

			if (this.data.type == "com.artesia.search.facet.FacetDateRangeFieldRestriction") {
				let customDateRangeHolder = this.parentNode;
				let startDate = customDateRangeHolder.querySelector("#startDate").value;
				let endDate = customDateRangeHolder.querySelector("#endDate").value;

				if (startDate || endDate) {
					this.data.value.start_date = startDate ? startDate.toISOString() : null;
					this.data.value.end_date = endDate ? endDate.toISOString() : null;
					hasData = true;
				}
			}
			if (this.data.type == "com.artesia.search.facet.FacetDateIntervalFieldRestriction") {
				let customDateRangeHolder = this.parentNode;
				let startDate = customDateRangeHolder.querySelector("#startDate").value;
				let endDate = customDateRangeHolder.querySelector("#endDate").value;
				if (startDate || endDate) {
					this.data.value.fixed_start_date = startDate ? startDate.toISOString() : null;
					this.data.value.fixed_end_date = endDate ? endDate.toISOString() : null;
					hasData = true;
				}
			}
			else if (this.data.type == "com.artesia.search.facet.FacetNumericRangeFieldRestriction" || this.data.type == "com.artesia.search.facet.FacetNumericIntervalFieldRestriction") {
				let customNumericRangeHolder = this.parentNode;
				this.data.value.start_value = $(customNumericRangeHolder).find("#startValue").val() || null;
				this.data.value.end_value = $(customNumericRangeHolder).find("#endValue").val() || null;

				if ((this.data.value.start_value && this.data.value.start_value.length > 0 && $.isNumeric(this.data.value.start_value))
					|| (this.data.value.end_value && this.data.value.end_value.length > 0 && $.isNumeric(this.data.value.end_value))) {
					hasData = true;
				}

				if (this.data.type == "com.artesia.search.facet.FacetNumericIntervalFieldRestriction") {
					let startValue = this.data.value.start_value;
					let endValue = this.data.value.end_value;

					if (otui.MetadataModelManager.isFilesizeField(this.data.fieldID)) {
						startValue = otui.FileUploadManager.getReadableFileSize(this.data.value.start_value);
						endValue = otui.FileUploadManager.getReadableFileSize(this.data.value.end_value);
					}

					this.data.value.interval_label = otui.tr("{0} - {1}", startValue ? startValue : 'before', endValue ? endValue : 'after');
				}

			}
			else if (this.data.type == "com.artesia.search.facet.FacetCascadingFieldRestriction") {
				hasData = true;
			}

			if (hasData) {
				_addFacet(this.data);
			}
			if (this.data.type == "com.artesia.search.facet.FacetCascadingFieldRestriction") {
				let view = otui.main.getChildView(OTDCPWDetailView) || otui.main.getChildView(OTDCPWDetailEditView);
				const { assetView, otdcpwSidebarView } = getAssetAndSidebarViews(view);

				if (otdcpwSidebarView && assetView) {
					updateSidebarFacets(assetView, otdcpwSidebarView);
				}
			}
		}

		if (otui.is(otui.modes.PHONE)) {
			let view = otui.JobsManager.getCurrentView(event);

			if (view) {
				otui.DialogUtils.cancelDialog(event.currentTarget);
			}
		}
	};

	// Helper to get assetView and otdcpwSidebarView from current view
    function getAssetAndSidebarViews(view, assetView) {
        let otdcpwSidebarView;
        if (view instanceof OTDCPWDetailView) {
            if (!assetView) {
                assetView = view.getChildView(OTDCPWAssetsViewWrapper).getChildView(OTDCPWAssetsView);
            }
            otdcpwSidebarView = view.getChildView(OTDCPWAssetsViewWrapper).getChildView(OTDCPWAssetsSidebarView);
        } else if (view instanceof OTDCPWDetailEditView) {
            if (!assetView) {
                assetView = view.getChildView(OTDCPWAssetsWrapperEdit).getChildView(OTDCPWAssetsEditView);
            }
            otdcpwSidebarView = view.getChildView(OTDCPWAssetsWrapperEdit).getChildView(OTDCPWAssetsSidebarEdit);
        }
        return { assetView, otdcpwSidebarView };
    }

    // Helper to update sidebar facets after changes
    function updateSidebarFacets(assetView, otdcpwSidebarView) {
        if (otdcpwSidebarView) {
            $('.ot-facets-base-holder').remove();
            otdcpwSidebarView.properties.appliedFacets = assetView.properties.appliedFacets;
            let assetFacetView = otdcpwSidebarView.getChildView(OTDCPWAssetsFacetsView);
            assetFacetView.properties.appliedFacets = assetView.properties.appliedFacets;
            assetFacetView.properties.facetsRendered = false;
            assetFacetView.properties.needsUpdate = true;
            delete assetFacetView.properties.isViewRendered;
            assetFacetView.onShow();
        }
    }

	/**
	 * @class OTDCPWAssetsFacetsView
	 * @mixes Views.asView
	 * @mixes Views.asViewModule
	 * @mixes Views.asRoutable
	 * @mixes withContent
	 * @constructor
	 * @param {OTDCPWAssetsFacetsView.properties} props Properties for this view.
	 * @classdesc
	 * @namespace OTDCPWAssetsFacetsView
	 */
	exports.OTDCPWAssetsFacetsView = otui.define("OTDCPWAssetsFacetsView", ['withContent'],
		/** @lends OTDCPWAssetsFacetsView
		 *  @this OTDCPWAssetsFacetsView */
		function () {
			this.properties =
			{
				'name': "OTDCPWAssetsFacets",
				'title': otui.tr("Filters"),
				'type': 'folder',
				'savedFacetValueList': {},
				savedFacetList: []
			};

			let _parentView;

			/**
			 * This function loads the content for a facets view and executes the supplied callback once loading has completed.
			 * @internal
			 */
			this._initContent = function initFacetsView(self, callback) {
				_parentView = self.internalProperty("parentView");

				let content = self.getTemplate(otui.is(otui.modes.PHONE) ? "content_phone" : "content", undefined, $);

				if (otui.is(otui.modes.PHONE) && self.properties.parentViewID) {
					_parentView = otui.viewHolder.view(self.properties.parentViewID);
				}

				OTDCPWAssetsFacetsView.content = content;

				let appliedFacets = _parentView.properties.appliedFacets ? JSON.parse(_parentView.properties.appliedFacets).facets : [];

				if (otui.is(otui.modes.PHONE)) {
					let clearFiltersButton = content.find('.ot-remove-all');
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
					self.populateFacets(self, appliedFacets, callback);
				}
				else {
					//content.find(".ot-facets-root .ot-facet").append("<span class='ot-facet-filters-none'>" + NOFILTERSMESSAGE + "</span>");
					callback(content);
				}
			};

			this.populateFacets = function (self, appliedFacets, callback) {
				let pageProperties = OTDCPWDetailView.getDefaultPageProperties();
				pageProperties.sortField = pageProperties.sortFieldforFacets;
				if (_parentView.constructor.name === "OTDCPWAssetsSidebarView" || _parentView.constructor.name === "OTDCPWAssetsSidebarEdit")
					_parentView.storedProperty("pageProperties", pageProperties);

				let data;

				if (otui.is(otui.modes.PHONE)) {
					data = JSON.parse(JSON.stringify(_parentView.properties));
					data.pageProperties = pageProperties;
				}
				else
					data = _parentView.properties;

				data.folderFilterType = "direct";
				data.forFacetsOnly = true;

				let preferenceID = PageManager.getFieldPreferenceName();
				let extraFields = PageManager.getExtraFields();

				let properties = _parentView ? _parentView.properties : {};
				if (properties.searchID !== undefined && properties.searchID !== "none" && otui.is(otui.modes.PHONE)) {
					let newProperties = {};
					Object.assign(newProperties, properties);
					if (properties.collectionID) {
						newProperties.searchID = properties.collectionID + "_" + properties.searchConfigID + "_" + "undefined";
					}
					else {
						newProperties.searchID = properties.nodeID ? properties.nodeID + "_" + properties.searchConfigID + "_" + "undefined" : properties.sourceName;
					}
					properties = newProperties;
				}

				let facetConfigurationName = '';
				if (OTDCUtilities.isPWContext()) {
					defaultFacetName = OTDCUtilities.defaultPWAssetFacetName;
					facetConfigurationName = (OTDCPWWorkspaceManager.config || {}).assetFacetConfig || defaultFacetName;
				} else {
					defaultFacetName = OTDCUtilities.defaultBWAssetFacetName;
					facetConfigurationName = (OTDCBWWorkspaceManager.config || {}).assetFacetConfig || defaultFacetName;
				}
				if (!otui.is(otui.modes.PHONE) && properties && (properties.searchID || properties.nodeID) && (!properties.savedSearchID || properties.savedSearchID === "none")) {
					// Clearing the previously cached facets.
					if (properties.nodeID) {
						OTDCPWAssetSearchManager.clearSearchForKeyword(properties.nodeID);
					}
				}

				//Check if facets not applied, if not make your own call
				let noFacets = !(JSON.parse(_parentView.properties.appliedFacets).facets.length);
				data.searchConfigID = "none";
				let assetsWrapper = _parentView.internalProperties.parentView;
				data.nodeID = assetsWrapper.properties.folderID;
				if (noFacets) {
					OTDCPWAssetSearchManager.getFolderAssetsByKeyword("*", 0, PageManager.assetsPerPage, preferenceID, extraFields, data, function () { });
				}
				if (assetsWrapper.properties.folderID) {
					otui.services.pwfacetsResult.readForAssets(properties, facetConfigurationName, defaultFacetName, function (facets, pluginId) {
						/**
						 * Maintain a flag to prevent re-rendering of facets view which is leading
						 * to prevent showing data incorrectly under a facet.
						 */
						if (!!self.properties.isViewRendered) {
							delete self.properties.isViewRendered;
							return;
						}

						let contentArea = self.contentArea();
						if (contentArea.find(".ot-facets-root .ot-facet").length == 0) contentArea = OTDCPWAssetsFacetsView.content;
						//contentArea.find(".ot-facets-root .ot-facet").empty();

						self.buildFacets(contentArea, JSON.parse(JSON.stringify(facets)));

						if (facets.length == 0) {
							contentArea.find(".ot-facets-root .ot-facet").append("<span class='ot-facet-filters-none'>" + NOFILTERSMESSAGE + "</span>");
						}
						if (pluginId === "ARTESIA.PLUGIN.SEARCH.DATABASE" || self.properties.keywordSearchAllowed === false) {
							let keywordEl = contentArea.find('.ot-facets-text-filter-holder');
							if (keywordEl && keywordEl.length > 0) {
								keywordEl.hide();
							}
						}
						if (callback) callback(contentArea);
						self.properties.isViewRendered = true;
					});
				}
			};

			this.bind("show", function (event) {
				//Adding below check to prevent further logic execution, when facets filter accordion is not clicked
				if (!this.internalProperties.physicalContent) {
					return;
				}
				pwSidebarView = (this.internalProperties || {}).parentView;
				this.properties.needsUpdate = true;
				_parentView = pwSidebarView;
				//TODO
				_parentView.properties.appliedFacets = JSON.stringify({ 'facets': [] });

				self = this;

				//load facet configuraiton at begining of facet view rendering
				let facetConfigurationName = '';
				if (OTDCUtilities.isPWContext()) {
					defaultFacetName = OTDCUtilities.defaultPWAssetFacetName;
					facetConfigurationName = (OTDCPWWorkspaceManager.config || {}).assetFacetConfig || defaultFacetName;
				} else {
					defaultFacetName = OTDCUtilities.defaultBWAssetFacetName;
					facetConfigurationName = (OTDCBWWorkspaceManager.config || {}).assetFacetConfig || defaultFacetName;
				}
				OTDCPWFacetsManager.readFacets(facetConfigurationName, defaultFacetName, function () {
					self.onShow();
				});
			});

			this.onShow = function () {
				let show = this.properties.needsUpdate && !this.properties.facetsRendered;
				if (otui.is(otui.modes.PHONE))
					show = this.properties.needsUpdate && _parentView && !this.properties.facetsRendered;

				if (show) {
					this.properties.needsUpdate = false;
					this.properties.facetsRendered = true;
					this.updateFacets(JSON.parse(_parentView.properties.appliedFacets).facets);
				}
			};

			this.isFacetApplied = function (facet) {
				let appliedFacets = _parentView.properties.appliedFacets ? JSON.parse(_parentView.properties.appliedFacets) : null;
				let found = false;

				if (appliedFacets)
					for (let i = 0; i < appliedFacets.facets.length; i++) {
						if (appliedFacets.facets[i].field_id == facet.fieldID) {
							for (let j = 0; j < appliedFacets.facets[i].value_list.length; j++) {
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
				let appliedFacets = _parentView.properties.appliedFacets ? JSON.parse(_parentView.properties.appliedFacets) : null;
				let found = false;

				if (appliedFacets) {
					for (let i = 0; (i < appliedFacets.facets.length) && !found; i++) {
						found = (appliedFacets.facets[i].field_id === facet._facet_field_request.field_id);
					}
				}

				return found;

			};


			this.getAppliedFacetValues = function (field_id) {
				let appliedFacets = _parentView.properties.appliedFacets ? JSON.parse(_parentView.properties.appliedFacets) : null;

				if (appliedFacets) {
					for (let i = 0; i < appliedFacets.facets.length; i++) {
						if (appliedFacets.facets[i].field_id === field_id) {
							for (let j = 0; j < appliedFacets.facets[i].value_list.length; j++) {
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
				let facetsBase = this.getTemplate("FacetsBase", undefined, $);
				let facetsBaseClone = null;
				let facetTemplateMaster = null;
				let facetTemplateClone = null;
				let elementsToRemoveString = null;
				let elementsToRemove = null;
				let removedElements;
				let insertAnchor = null;
				let input = null;
				let data = null;
				let facetTitle = "";
				let facetsToExpand = [];
				let defaultFacetDisplayCount = OTDCPWFacetsManager.currentFacetConfiguration.default_facets_displayed;

				facets = facets && Array.isArray(facets) ? facets : [];

				this.properties.savedFacetList = facets.slice(0);

				this.renderFacetSet(content);


			};

			this.renderFacetSet = function (content) {

				let defaultFacetDisplayCount = OTDCPWFacetsManager.currentFacetConfiguration.default_facets_displayed;
				let facets = [];
				if (this.properties.savedFacetList.length > defaultFacetDisplayCount) {
					facets = this.properties.savedFacetList.splice(0, defaultFacetDisplayCount);
				}
				else {
					facets = this.properties.savedFacetList;
					this.properties.savedFacetList = [];
				}

				for (let i = 0; i < facets.length; i++) {
					this.renderFacet(facets[i], content);
				}

				let contentArea = this.contentArea();
				if (contentArea.find('.ot-contentblock').length)
					this.unblockContent();

			};



			this.renderFacet = function (facet, content) {
				let facetsBase = this.getTemplate("FacetsBase", undefined, $);
				let facetsBaseClone = null;
				let facetTemplateMaster = null;
				let elementsToRemoveString = null;
				let elementsToRemove = null;
				let removedElements;
				let insertAnchor = null;
				let input = null;
				let data = null;
				let facetTitle = "";
				let facetsToExpand = [];
				let isFileSizeFacet = otui.MetadataModelManager.isFilesizeField(facet._facet_field_request.field_id) || false;
				let appliedFacetValues = otui.is(otui.modes.PHONE) && this.getAppliedFacetValues(facet["_facet_field_request"].field_id);

				facetsBaseClone = facetsBase.clone();

				facetTitle = otui.MetadataModelManager.getDisplayName(facet._facet_field_request.field_id) || facet._facet_field_request.field_id;

				if (facet._parent_path && facet._parent_path.component_list)
					facetTitle += ": " + facet._parent_path.component_list.join("/");

				facetsBaseClone.find(".ot-facets-base-title").text(facetTitle);

				facetsBaseClone.find(".ot-facets-base-title").on("mouseenter", function (event) {
					let $this = $(this);

					if (this.offsetWidth < this.scrollWidth && !$this.attr('title')) {
						$this.attr('title', $this.text());
					}
				});

				facetTemplateMaster = this.getTemplate("facet-type", facet, $);
				elementsToRemoveString = facetTemplateMaster.attr("ot-norepeat");
				elementsToRemove = elementsToRemoveString ? elementsToRemoveString.split(",") : [];

				removedElements = [];

				for (let k = 0; k < elementsToRemove.length; k++) {
					removedElements = facetTemplateMaster.find("#" + elementsToRemove[k]).remove();
				}

				let displayAllFacetValues = !OTDCPWFacetsManager.currentFacetConfiguration.default_facet_values_displayed;
				this.renderFacetValue(facet, facetsBaseClone, facetTemplateMaster, displayAllFacetValues);

				for (let l = 0; l < removedElements.length; l++) {
					insertAnchor = $(removedElements[l]).attr("ot-insert");

					data = {};
					data.fieldID = facet["_facet_field_request"].field_id;
					data.multiSelect = facet["_facet_field_request"].multi_select;
					data.name = facet["_facet_field_request"].field_name;

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
					let fileSizeLabels = facetsBaseClone.find(".ot-facet-filesize-label");
					fileSizeLabels[0].setAttribute("ot-token", "From (in bytes)");
					fileSizeLabels[1].setAttribute("ot-token", "To (in bytes)");
				}

				content.find(".ot-facets-root .ot-facet").append(facetsBaseClone);
			}

			this.renderFacetValue = function (facet, facetsBaseClone, facetValueTemplate, showAll) {
				let isFileSizeFacet = false, facetTemplateClone;
				let facetValueList = facet["_facet_value_list"];
				let hasAppliedFacet = this.hasAppliedFacets(facet);
				let j = 0;
				let processNextIteration = true;
				let facetValueNumber = facetValueList.length;
				let valueOffset = parseInt(facet["_value_offset"] || 0);
				for (; j < facetValueNumber && processNextIteration; j++) {
					facetTemplateClone = facetValueTemplate.clone();

					data = {};
					data.fieldID = facet["_facet_field_request"].field_id;
					data.multiSelect = facet["_facet_field_request"].multi_select;
					data.name = facet.field_name;
					if (facet.type == "com.artesia.search.facet.FacetSimpleFieldResponse") {
						data.type = "com.artesia.search.facet.FacetSimpleFieldRestriction";
						let textValue = otui.TranslationManager.getTranslation(facet["_facet_value_list"][j].value);
						facetTemplateClone.find('.ot-facet-simple-label').text(textValue || otui.tr("undefined"));
						facetTemplateClone.find('.ot-facet-simple-total').text(facet["_facet_value_list"][j].asset_count);
						data.value = facet["_facet_value_list"][j].value || null;
					}
					else if (facet.type == "com.artesia.search.facet.FacetNumericRangeFieldResponse") {
						data.type = "com.artesia.search.facet.FacetNumericRangeFieldRestriction";
						let startValue = facet["_facet_value_list"][j].numeric_range.start_value;
						let endValue = facet["_facet_value_list"][j].numeric_range.end_value;
						let textValue = undefined;

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
						let textValue = otui.TranslationManager.getTranslation(facet["_facet_value_list"][j].numeric_interval.interval_label);
						facet["_facet_value_list"][j].numeric_interval.interval_label = textValue;

						isFileSizeFacet = otui.MetadataModelManager.isFilesizeField(facet._facet_field_request.field_id);

						facetTemplateClone.find('.ot-facet-simple-label').text(textValue);
						facetTemplateClone.find('.ot-facet-simple-total').text(facet["_facet_value_list"][j].asset_count);
						data.value = facet["_facet_value_list"][j].numeric_interval;
					}
					else if (facet.type == "com.artesia.search.facet.FacetDateIntervalFieldResponse") {
						data.type = "com.artesia.search.facet.FacetDateIntervalFieldRestriction";
						let textValue = otui.TranslationManager.getTranslation(facet["_facet_value_list"][j].date_interval.interval_label);

						if (facet["_facet_value_list"][j].token_expansion_list && facet["_facet_value_list"][j].token_expansion_list.length) {
							let index = 0;
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
						let pathCompList = ((facet["_facet_value_list"][j].path || {}).component_list || []);
						facetTemplateClone.find('.ot-facet-cascading-label').text(pathCompList[pathCompList.length - 1] || "undefined");
						facetTemplateClone.find('.ot-facet-simple-total').text(facet["_facet_value_list"][j].asset_count);
						data.value = facet["_facet_value_list"][j].path;
					}

					facetTemplateClone.find('.ot-facet-simple-label').on("mouseenter", function (event) {
						let $this = $(this);

						if (this.offsetWidth < this.scrollWidth && !$this.attr('title')) {
							$this.attr('title', $this.text());
						}
					});

					facetTemplateClone.find('.ot-facet-cascading-label').on("mouseenter", function (event) {
						let $this = $(this);

						if (this.offsetWidth < this.scrollWidth && !$this.attr('title')) {
							$this.attr('title', $this.text());
						}
					});

					if (facet.type == "com.artesia.search.facet.FacetSimpleFieldResponse" || facet.type == "com.artesia.search.facet.FacetNumericRangeFieldResponse" || facet.type == "com.artesia.search.facet.FacetDateRangeFieldResponse" || facet.type === "com.artesia.search.facet.FacetNumericIntervalFieldResponse" || facet.type === "com.artesia.search.facet.FacetDateIntervalFieldResponse") {
						input = facetTemplateClone.find('.ot-facet-simple-checkbox');

						input.attr("id", data.fieldID + "_" + data.value + "_" + (j + valueOffset));

						let checkboxLabel = facetTemplateClone.find(".ot-facet-simple-label");
						checkboxLabel.attr("for", data.fieldID + "_" + data.value + "_" + (j + valueOffset));
						checkboxLabel.on("mouseenter", function (event) {
							let $this = $(this);

							if (this.offsetWidth < this.scrollWidth && !$this.attr('title')) {
								$this.attr('title', $this.text());
							}
						});

						input.attr("type", data.multiSelect ? "checkbox" : "radio");
						input.addClass(data.multiSelect ? "ot-checkbox" : "ot-radio");

						input.change(_addRemoveFacetRestriction);
						data.name = facet.field_name;
						input[0].data = data;

						let isFacetApplied = this.isFacetApplied(data);
						input.prop("checked", isFacetApplied);
					}
					else if (facet.type == "com.artesia.search.facet.FacetCascadingFieldResponse") {
						input = facetTemplateClone.find('.ot-facet-cascading-label');
						data.name = facet.field_name;
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
					let clonedEl = facetTemplateClone.clone();
					document.body.appendChild(clonedEl[0]);
					facetsBaseClone.find(".ot-facets-base-content").attr("style", "height: " + (clonedEl.outerHeight(true) * OTDCPWFacetsManager.currentFacetConfiguration.default_facet_values_displayed - parseInt(clonedEl.css("margin-top"))) + "px;");
					document.body.removeChild(clonedEl[0]);
				}
				else if (j === facetValueNumber && j > OTDCPWFacetsManager.currentFacetConfiguration.default_facet_values_displayed && !showAll) {
					let clonedEl = facetTemplateClone.clone();
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
				let self = this;

				this.populateFacets(self, facets);
			};
			this.saveFacetValueList = function (facet) {
				this.properties.savedFacetValueList[facet._facet_field_request.field_id] = facet;
			};
			this.getSavedFacetValueList = function (fieldId) {
				return this.properties.savedFacetValueList[fieldId];
			};
		});

	OTDCPWAssetsFacetsView.content = undefined;

	OTDCPWAssetsFacetsView.hideFacets = function () {
		let facetsRoot = OTDCPWAssetsFacetsView.content.find(".ot-facets-root");
		let facets = facetsRoot.children();

		if (facets.length > OTDCPWFacetsManager.currentFacetConfiguration.default_facets_displayed) {
			let iterationCount = 1;
			let lastFacetSpan = null;
			let isMoreFacets = false;
			lastFacetSpan = $(facets[facets.length - 1]).find("ot-layout > span[style]");
			if (lastFacetSpan.length > 0) {
				if (arguments != null && arguments.length > 0) {
					iterationCount = arguments[0];
				}
				// Added this condition to verify that left side facet layout is loaded or not.
				if (lastFacetSpan[0].attributes["style"].value.indexOf("calc") == -1 && iterationCount < 20) {
					iterationCount = iterationCount + 1;
					setTimeout("OTDCPWAssetsFacetsView.hideFacets(" + iterationCount + ")", 100);
					return;
				}
			}
			for (let i = 0; i < facets.length; i++) {
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

	/**
	 * Function to clear all filters when Clear button is clicked
	 */
	OTDCPWAssetsFacetsView.handleClearFilters = function handleClearFilters(assetView) {
		let view = otui.main.getChildView(OTDCPWDetailView) || otui.main.getChildView(OTDCPWDetailEditView);
        const { assetView: av, otdcpwSidebarView } = getAssetAndSidebarViews(view, assetView);
        assetView = av;

        let facetRestrictions = JSON.parse(assetView.storedProperty("appliedFacets"));
        facetRestrictions.facets = [];

        OTDCPWAssetSearchManager.clearSearchForKeyword(view.properties.nodeID);

        if (assetView instanceof OTDCPWAssetsView || assetView instanceof OTDCPWAssetsEditView) {
            assetView.properties.appliedFacets = JSON.stringify(facetRestrictions);
            assetView.properties.pageProperties.page = '0';
            updateSidebarFacets(assetView, otdcpwSidebarView);
        }
	}

	OTDCPWAssetsFacetsView.expandFacetValueList = function (event, facetContainer) {
		let fieldId = facetContainer.getAttribute("ot-field-id");
		let currentView = otui.Views.containing(facetContainer);
		let baseHeight = 0;

		if (currentView instanceof OTDCPWAssetsSidebarView) {
			currentView = currentView.childViews[1];
		}

		if ($(facetContainer).find(".ot-facets-base-more").attr("ot-facet-showing") == "false") {
			let facet = currentView.getSavedFacetValueList(fieldId);

			if (!facetContainer.hasAttribute("ot-loaded-all-vals") && facet) {
				let facetTemplateMaster = currentView.getTemplate("facet-type", facet, $);
				let elementsToRemoveString = facetTemplateMaster.attr("ot-norepeat");
				let elementsToRemove = elementsToRemoveString ? elementsToRemoveString.split(",") : [];


				for (let k = 0; k < elementsToRemove.length; k++) {
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

	OTDCPWAssetsFacetsView.toggleFacet = function (event) {
		let facetContainer = event.currentTarget.parentNode;
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

	OTDCPWAssetsFacetsView.handleKeyUp = function (event) {
		let input = $(event.currentTarget);
		if (event.keyCode == 13) {
			OTDCPWAssetsFacetsView.performKeywordSearch();
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

	OTDCPWAssetsFacetsView._removeFacet = function(assetView, eventDetail){

		let view = otui.main.getChildView(OTDCPWDetailView) || otui.main.getChildView(OTDCPWDetailEditView);
        const { assetView: av, otdcpwSidebarView } = getAssetAndSidebarViews(view, assetView);
        assetView = av;

        let modifiedFacets = eventDetail.appliedFacets;

        assetView.properties.appliedFacets = JSON.stringify({'facets':modifiedFacets});
        assetView.properties.pageProperties.page = '0';

        updateSidebarFacets(assetView, otdcpwSidebarView);
	};

	OTDCPWAssetsFacetsView.performKeywordSearch = function (assetView, eventDetail) {

		if(!assetView) {
            let view = otui.main.getChildView(OTDCPWDetailView) || otui.main.getChildView(OTDCPWDetailEditView);
            const { assetView: av } = getAssetAndSidebarViews(view, assetView);
            assetView = av;
        }

        let inputVal = [];

        let appliedFacets = JSON.parse(assetView.storedProperty("appliedFacets"));
        let searchFacet = appliedFacets.facets.find(facet => facet.field_id === "com.artesia.search.facet.FacetRefineByKeywordFieldRestriction");

        if(searchFacet){
            inputVal = searchFacet.value_list;
        }
        
        let modifiedSearchFacet = eventDetail.appliedFacets.find(facet => facet.field_id === "com.artesia.search.facet.FacetRefineByKeywordFieldRestriction");
        
        inputVal.push(modifiedSearchFacet.value_list[modifiedSearchFacet.value_list.length -1]);

        if (assetView && inputVal.length > 0) {
            if (assetView instanceof OTDCPWAssetsView || assetView instanceof OTDCPWAssetsEditView) {
                assetView.properties.appliedFacets = _addFacet({
                    "type": "com.artesia.search.facet.FacetRefineByKeywordFieldRestriction",
                    "fieldID": "com.artesia.search.facet.FacetRefineByKeywordFieldRestriction", "value": inputVal
                });
                assetView.properties.pageProperties.page = '0';
            }
        }
	};
})(window);
