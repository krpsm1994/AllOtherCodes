(function (exports) {
	
	var OTDCPWRelatedWorkspacesView = exports.OTDCPWRelatedWorkspacesView = 
        otui.define("OTDCPWRelatedWorkspacesView", ["withContent"], function() {
			this.paginationData = {
				currentPage: 1,
				pageSize: 25,
				totalItems: 0,
				totalPages: 0,
			}
            this.properties = {
					'name': 'OTDCPWRelatedWorkspacesView', // Name of the view
					'title': otui.tr("Related Workspaces"), // Title of the view
					'type': 'OTDCPWRelatedWorkspacesView',
					'loaded': false
				};
			this.boTypesLoaded = false;
			this._initContent = function _initContent(self, callback) {
				self.blockContent();
				var template = self.getTemplate("content");
				var content = $(otui.fragmentContent(template));
				callback(content);
				if(!this.boTypesLoaded){
					loadRelatedWorkspacesInitialContent.call(this);
				}
			};
			
			this.bind("attached-to-view", function () {
				var parentView = this.internalProperties.parentView;
				if (parentView) {
					parentView.bind("got-asset", loadAssetToRelatedWorkspaces.bind(this));
				}
			});
	});

	function loadAssetToRelatedWorkspaces() {
		if(!this.boTypesLoaded){
			loadRelatedWorkspacesInitialContent.call(this);
		}
	}
	OTDCPWRelatedWorkspacesView.noBoTypeFound = false;


	function loadRelatedWorkspacesInitialContent() {
		let emptyMessageEl = $(document).find('.otdcpw-related-workspaces-empty-message');
		emptyMessageEl.show();
		let errorDiv = $(document).find('.otdcpw-no-botype-message');
		if(this.noBoTypeFound){
			errorDiv.show();
			let workspaceTableArea = $(document).find('.otdcpw-related-workspaces-table');
			workspaceTableArea.hide();
			let selectionContainer = $(document).find('.otdcpw-related-workspaces-selection-container');
			selectionContainer.hide();
			let emptyMessageEl = $(document).find('.otdcpw-related-workspaces-empty-message');
			emptyMessageEl.hide();
			let boworkspaceContainer = $(document).find('.otdcpw-related-workspaces-container');
			boworkspaceContainer.hide();
			return;
		}
		errorDiv.hide();
		let assetMetadata  = this.internalProperties.parentView.internalProperties?.asset;
		
		if (assetMetadata && assetMetadata.metadata && assetMetadata.metadata.metadata_element_list) {
			
			// Retrieve OTDCBW.BO.TYPE from metadata
			let boTypeId = null;
			
			// Find the BO Details field group
			var boDetailsGroup = assetMetadata.metadata.metadata_element_list.find(function(fg) {
				return fg.id === 'OTDCBW.BODETAILS.FG';
			});
			
			if (boDetailsGroup && boDetailsGroup.metadata_element_list) {
				// Extract BO Type
				var boTypeField = boDetailsGroup.metadata_element_list.find(function(field) {
					return field.id === 'OTDCBW.BO.TYPE';
				});
				if (boTypeField && boTypeField.value && boTypeField.value.value && boTypeField.value.value.field_value) {
					boTypeId = boTypeField.value.value.field_value.value;
				}
			}

			if (!boTypeId) {
				this.noBoTypeFound = true;
				loadRelatedWorkspacesInitialContent.call(this);
				return;
			};
			this.noBoTypeFound = false;
			OTDCPWRelatedWorkspacesView.fetchBoTypes.call(this, (boTypeId));
			var workspaceTableArea = $(document).find('.otdcpw-related-workspaces-table');
			workspaceTableArea.hide();
		}
			
	}

	OTDCPWRelatedWorkspacesView.boTypes = [];

	OTDCPWRelatedWorkspacesView.populateDropdown = function () {
		if (!OTDCPWRelatedWorkspacesView.boTypes || !OTDCPWRelatedWorkspacesView.boTypes.length) {
			return;
		}
		
		var optionsContainer = $(document).find('.otdcpw-bo-type-options');
		if (!optionsContainer.length) {
			return;
		}
		
		optionsContainer.empty();
		
		// Set the first option as selected by default
		if (OTDCPWRelatedWorkspacesView.boTypes.length > 0) {
			var selectedText = $(document).find('.otdcpw-bo-type-selected-text');
			if (selectedText.length) {
				selectedText.text(OTDCPWRelatedWorkspacesView.boTypes[0].displayName);
			}
			OTDCPWRelatedWorkspacesView.selectedOption = OTDCPWRelatedWorkspacesView.boTypes[0];
		}
		
		// Populate dropdown options from boTypes array
		OTDCPWRelatedWorkspacesView.boTypes.forEach(function(boType) {
			var optionDiv = document.createElement('div');
			optionDiv.className = 'otdcpw-bo-type-option';
			optionDiv.textContent = boType.displayName;
			optionDiv.setAttribute('data-value', boType.boTypeId);
			optionDiv.onclick = function(event) {
				OTDCPWRelatedWorkspacesView.selectOption(event, boType);
			};
			optionsContainer.append(optionDiv);
		});
	};

	OTDCPWRelatedWorkspacesView.toggleDropdown = function (event) {
		event.stopPropagation();
		var dropdown = $(event.currentTarget).closest('.otdcpw-bo-type-dropdown');
		var options = dropdown.find('.otdcpw-bo-type-options');
		var isVisible = options.is(':visible');
		
		if (isVisible) {
			options.hide();
			dropdown.removeClass('open');
		} else {
			options.show();
			OTDCPWRelatedWorkspacesView.populateDropdown.call(this);
			dropdown.addClass('open');
		}
	};

	OTDCPWRelatedWorkspacesView.selectedOption = null

	OTDCPWRelatedWorkspacesView.selectOption = function (event, boType) {
		event.stopPropagation();
		var dropdown = $(event.currentTarget).closest('.otdcpw-bo-type-dropdown');
		var selectedText = dropdown.find('.otdcpw-bo-type-selected-text');
		
		selectedText.text(boType.displayName);
		dropdown.find('.otdcpw-bo-type-options').hide();
		dropdown.removeClass('open');

		OTDCPWRelatedWorkspacesView.selectedOption = boType;
	};

	// Close related workspaces BOType dropdown when clicking outside
	$(document).on('click', function(event) {
		if (!$(event.target).closest('.otdcpw-bo-type-dropdown').length) {
			$('.otdcpw-bo-type-options').hide();
			$('.otdcpw-bo-type-dropdown').removeClass('open');
		}
	});

	OTDCPWRelatedWorkspacesView.fetchBoTypes = function (boTypeId) {
		
		if (!boTypeId) return;

		let boTypeCallback = function(boTypesResponse) {

			OTDCPWRelatedWorkspacesView.boTypes = boTypesResponse.relatedBOTypes;
			// Set the first option as selected by default
			if (OTDCPWRelatedWorkspacesView.boTypes.length > 0) {
				var selectedText = $(document).find('.otdcpw-bo-type-selected-text');
				if (selectedText.length) {
					selectedText.text(OTDCPWRelatedWorkspacesView.boTypes[0].displayName);
					this.boTypesLoaded = true;
				}
				OTDCPWRelatedWorkspacesView.selectedOption = OTDCPWRelatedWorkspacesView.boTypes[0];
			}
		}

		OTDCUtilities.fetchBOTypes(boTypeCallback, boTypeId, this);	
	}

	OTDCPWRelatedWorkspacesView.updatePaginationElements = function(paginationData) {
		let previousPage = $('#otdcpw-prev-page');
		let nextPage = $('#otdcpw-next-page');
		let pageInput = $('#otdcpw-current-page');
		let pageSizeSelect = $('#otdcpw-page-size');
		let totalPagesSpan = $('#otdcpw-total-pages');
		
		if(paginationData.currentPage <= 1){
			previousPage.attr('disabled', true);
		} else {
			previousPage.attr('disabled', false);
		}
		if(paginationData.currentPage >= paginationData.totalPages){
			nextPage.attr('disabled', true);
		} else {
			nextPage.attr('disabled', false);
		}
		
		// Update input attributes and value
		pageInput.attr('max', paginationData.totalPages);
		pageInput.val(paginationData.currentPage);
		pageSizeSelect.val(paginationData.pageSize);
		totalPagesSpan.text(paginationData.totalPages);
		
		// Remove old event listeners to avoid duplicates
		pageInput.off('input blur');
		
		// Add input validation
		pageInput.on('input', function() {
			let value = parseInt($(this).val());
			if (value < 1) {
				$(this).val(1);
			} else if (value > paginationData.totalPages) {
				$(this).val(paginationData.totalPages);
			}
		});
		
		// Reset to current page on blur if invalid
		pageInput.on('blur', function() {
			let value = parseInt($(this).val());
			if (isNaN(value) || value < 1 || value > paginationData.totalPages) {
				$(this).val(paginationData.currentPage);
			}
		});
	}

	OTDCPWRelatedWorkspacesView.fetchRelatedWorkspaces = function (event, paginationData) {
		let workspaceId ="";
		let pagination = undefined;
		let view = this;
		if(this instanceof OTDCPWRelatedWorkspacesView){
			//this will execute when pagination buttons are clicked as the context is maintained, in that case get the workspaceId and pagination info from the view properties
			workspaceId  = this.internalProperties.parentView.internalProperties?.asset.asset_id;
			pagination = this.paginationData;
		} else {
			//this will execute when BO Type is selected from dropdown as the context will be of the clicked element, in that case get the workspaceId from parent view and pagination info from argument passed
			view = otui.Views.containing(event.currentTarget);
			workspaceId = view.internalProperties.parentView.internalProperties?.asset.asset_id;
			let assetsPerPage = otui.PreferencesManager.preferences.assetsPerPage[0].values[0];
			pagination = view.paginationData;
			pagination.pageSize = assetsPerPage ? parseInt(assetsPerPage) : paginationData.pageSize;
		}

		let boDetails = {
			boTypeId: OTDCPWRelatedWorkspacesView.selectedOption.boTypeId,
			offset: (pagination.currentPage - 1) * pagination.pageSize,
			limit: pagination.pageSize,
			sortBy: 'boId',
			sortOrder: 'ASC',
			workspaceId: workspaceId
		}

		let fetchRelatedWorkspacesCallback = function (relatedWorkspacesResponse) {
			view.paginationData.totalItems = relatedWorkspacesResponse.pagination.totalCount;
			view.paginationData.totalPages = Math.ceil(relatedWorkspacesResponse.pagination.totalCount / view.paginationData.pageSize);
			OTDCPWRelatedWorkspacesView.updatePaginationElements(view.paginationData);
			OTDCPWRelatedWorkspacesView.generateRelatedWorkspacesTable.call(view, relatedWorkspacesResponse.workspaces);
		}

		OTDCUtilities.fetchRelatedWorkspaces(fetchRelatedWorkspacesCallback, view, boDetails);
	}

	OTDCPWRelatedWorkspacesView.generateRelatedWorkspacesTable = function (workspaces) {
		let emptyMessageEl = $(document).find('.otdcpw-related-workspaces-empty-message');
		let noResultsEl = $(document).find('.otdcpw-no-results');
		if (workspaces.length > 0) {
			emptyMessageEl.hide();
			noResultsEl.hide();
			var workspaceTableArea = $(document).find('.otdcpw-related-workspaces-table');
			workspaceTableArea.show();
			var tbody = $(document).find('.otdcpw-related-workspaces-tbody');
			tbody.empty();
			var currentUrl = window.location.href.split('?')[0];
			workspaces.forEach(function (workspace) {
				var workspaceUrl = currentUrl + "?p=otdcWorkspace/"+workspace.workspaceId+"@OTDCPWAssetsViewWrapper@";
				var row = '<tr class="otdcpw-related-workspaces-row">' +
					'<td class="col-boid" title="' + workspace.boId + '"><a class="otdcpw-related-workspaces-link">' + workspace.boId + '</a><a href="'+workspaceUrl+'" target="_blank"><img class="otdcpw-related-workspaces-icon" src="ot_damlink_pw/style/img/action_open_in_new_tab_grey.svg" alt="Open workspace in new tab" title="Open workspace in new tab"/></a></td>' +
					'<td class="col-boname">' + workspace.boName + '</td>' +
					'<td class="col-bodesc">' + workspace.boDesc + '</td>' +
					'</tr>';
				tbody.append(row);
			});
			
			// Add hover effect for icons
			$(document).find('.otdcpw-related-workspaces-icon').off('mouseenter mouseleave').on('mouseenter', function() {
				$(this).attr('src', 'ot_damlink_pw/style/img/action_open_in_new_tab_blue.svg');
			}).on('mouseleave', function() {
				$(this).attr('src', 'ot_damlink_pw/style/img/action_open_in_new_tab_grey.svg');
			});
		} else {
			//emptyMessageEl.show();
			noResultsEl.show();
		}
	}

	OTDCPWRelatedWorkspacesView.goToPreviousPage = function (event) {
		var view = otui.Views.containing(event.currentTarget);
		if (view.paginationData.currentPage > 1) {
			view.paginationData.currentPage--;
			OTDCPWRelatedWorkspacesView.fetchRelatedWorkspaces.call(view, view.paginationData);
			OTDCPWRelatedWorkspacesView.updatePaginationElements(view.paginationData);
		}
	}

	OTDCPWRelatedWorkspacesView.goToNextPage = function(event) {
		var view = otui.Views.containing(event.currentTarget);
		if (view.paginationData.currentPage < view.paginationData.totalPages) {
			view.paginationData.currentPage++;
			OTDCPWRelatedWorkspacesView.fetchRelatedWorkspaces.call(view, view.paginationData);
			OTDCPWRelatedWorkspacesView.updatePaginationElements(view.paginationData);
		}
	}

	OTDCPWRelatedWorkspacesView.goToPage = function(event, value) {
		var view = otui.Views.containing(event.currentTarget);
		var pageNumber = parseInt(value);
		
		// Validate page number
		if (isNaN(pageNumber) || pageNumber < 1 || pageNumber > view.paginationData.totalPages) {
			// Reset to current page if invalid
			$('#otdcpw-current-page').val(view.paginationData.currentPage);
			return;
		}
		
		if (pageNumber >= 1 && pageNumber <= view.paginationData.totalPages) {
			view.paginationData.currentPage = pageNumber;
			OTDCPWRelatedWorkspacesView.fetchRelatedWorkspaces.call(view, view.paginationData);
			OTDCPWRelatedWorkspacesView.updatePaginationElements(view.paginationData);
		}
	}

	OTDCPWRelatedWorkspacesView.changePageSize = function(event, value) {
		var view = otui.Views.containing(event.currentTarget);
		var pageSize = parseInt(value);
		if (pageSize > 0) {
			view.paginationData.pageSize = pageSize;
			view.paginationData.currentPage = 1; // Reset to first page when page size changes
			OTDCPWRelatedWorkspacesView.fetchRelatedWorkspaces.call(view, view.paginationData);
			OTDCPWRelatedWorkspacesView.updatePaginationElements(view.paginationData);
		}
	}

	//Product Workspace Detail View - Metadata screen
	var OTDCPWDetailMetadataView = exports.OTDCPWDetailMetadataView =
		otui.define("OTDCPWDetailMetadataView", ["withContent"],
			function () {
				this.properties = {
					'name': 'OTDCPWDetailMetadataView', // Name of the view
					'title': otui.tr("Metadata"),
					'type': 'OTDCPWDetailMetadataView',
					'loaded': false
				};
				this._initContent = function _initContent(self, callback) {
					self.blockContent();
					var template = self.getTemplate("content");
					var content = $(otui.fragmentContent(template));
					callback(content);
					if (this.properties.assetInfo) {
						loadMetadataContent.call(this, this.internalProperties.parentView, this.properties.assetInfo);
					}
				};
				this.bind("attached-to-view", function () {
					var parentView = this.internalProperties.parentView;

					if (parentView)
						parentView.bind("got-asset", loadMetadataContent.bind(this));
				});
			});

	function loadMetadataContent(parent, asset) {
		this.unblockContent();
		//Check if tab selected is Metadata
		if (parent.properties.OTDCPWDetailView_selected != "OTDCPWDetailMetadataView" || this.properties.loaded) {
			return;
		}
		//Set loaded
		this.properties.loaded = true;
		this.properties.loadedFG = [];

		//if active field group info exists, then preselect the same else the first field group
		var fgID = this.properties.assetInfo.metadata.metadata_element_list[0].id;
		OTDCPWDetailMetadataView.loadSidebar.call(this);
		var fgInfo = getFGInfo.call(this, fgID);
		var fgMetadata = getFgMetadata.call(this, fgID);
		OTDCPWDetailMetadataView.fillMetadata.call(this, fgInfo, fgMetadata);
	}
	OTDCPWDetailMetadataView.loadSidebar = function loadSidebar() {
		var fgList = this.properties.assetInfo.metadata.metadata_element_list;
		$(".otdcpw-left-nav").empty();
		for (var i = 0; i < fgList.length; i++) { 
			//var navEl = getNavOption(fgList[i].id, fgList[i].name, OTDCPWDetailMetadataView.handleOnClick);
			//MetadataModelManager.getModelDisplayName for model
			var localizedFieldGroupName = otui.MetadataModelManager.getCategoryDisplayName(fgList[i].id);
			var navEl = getNavOption(fgList[i].id, localizedFieldGroupName?localizedFieldGroupName:fgList[i].name, OTDCPWDetailMetadataView.handleOnClick);
			if (i == 0) {
				navEl.setAttribute("class", "otdcpw-detail-side-div otdcpw-detail-side-div-active");
			}
			$(".otdcpw-left-nav").append(navEl);
		}
	}
	var getNavOption = function getNavOption(id, val, handler) {
		var spanEl = document.createElement("span");
		if (val)
			spanEl.textContent = val;
		var div = document.createElement("div");
		div.setAttribute("class", "otdcpw-detail-side-div")
		div.setAttribute("id", id);
		div.addEventListener("click", handler);
		div.append(spanEl);
		return div;
	}
	OTDCPWDetailMetadataView.handleOnClick = function handleOnClick(event) {
		var oldActiveEl = $(".otdcpw-detail-side-div-active");
		oldActiveEl.removeClass("otdcpw-detail-side-div-active");
		event.currentTarget.setAttribute("class", "otdcpw-detail-side-div otdcpw-detail-side-div-active");
		OTDCPWDetailMetadataView.loadMetadata(event, oldActiveEl);
	}
	OTDCPWDetailMetadataView.loadMetadata = function loadMetadata(event, oldActiveEl) {
		var id = event.currentTarget.id;
		var view = otui.Views.containing(event.currentTarget);
		var fgMetadata = getFgMetadata.call(view, id);
		var fgInfo = getFGInfo.call(view, id);
		var elfound = false;
		var el = $(".otdcpw-right-view").detach();//Detach DOM
		//Detach and store the FG loaded Metadata
		for (var i = 0; i < view.properties.loadedFG.length; i++) {
			if (view.properties.loadedFG[i].id == oldActiveEl[0].id) {
				view.properties.loadedFG[i].el = el;
				elfound = true;
				break;
			}
		}
		if (!elfound) {
			view.properties.loadedFG.push({ id: oldActiveEl[0].id, el: el });
		}

		if (id === 'OTDC.PW.FG.VARIANTS') {
			// Extract the variants array
			$('#otdcpwVariantsTable').remove();
			$(".otdcpw-body").append(OTDCUtilities.generateVariantsTable(fgMetadata));
		} else {
			$('#otdcpwVariantsTable').remove();
			//Load the FG Metadata if found in stored	
			//Check if this FG is already loaded, if yes load the same 
			for (var i = 0; i < view.properties.loadedFG.length; i++) {
				if (view.properties.loadedFG[i].id == id) {
					$(".otdcpw-body").append(view.properties.loadedFG[i].el);
					return;
				}
			}
			//If not founded in stored, build the right view
			OTDCPWDetailMetadataView.fillMetadata(fgInfo, fgMetadata);
		}
	}

	 // Toggle panel on button click
	OTDCPWDetailMetadataView.handleExpandClick = function handleExpandClick(event) {
		let target = event.target; // Get the clicked element

		// Check if the clicked element is the button
		if ($(target).hasClass("expand-btn")) {
			$(target).closest("tr").next(".otdcpw-variants-expandable-panel").toggle();
		}
	}


	OTDCPWDetailMetadataView.fillMetadata = function fillMetadata(fgInfo, metadata) {
		var divEl = document.createElement("div");
		divEl.setAttribute("class", "otdcpw-right-view");
		var rowEl = getRowEl();
		for (var i = 0; i < metadata.length; i++) {
			//Get Field info
			var fieldInfo = fgInfo.find(function (element) { return element.id == metadata[i].id });
			if (metadata[i].type == OTDCPWWorkspaceManager.METADATA_TABULAR) {
				fieldInfo = fieldInfo.metadata_element_list;
				var colEl = handleTabularMetadata(metadata[i]);
				if (metadata[i].metadata_element_list.length != 1) {
					var isTable = true;
				}
			} else {
				var colEl = getColumnEl();
				var labelEl = getLabelEl(otui.MetadataModelManager.getDisplayName(metadata[i].id), fieldInfo.required);

				var labelVal;
				if (metadata[i].value && metadata[i].value.value) {
					var fldValue = metadata[i].value.value.display_value || metadata[i].value.value.value;
					if (fieldInfo.data_type == 'DATE' && fldValue) {
						try {
							fldValue = otui.formatDate(otui.DateFormat.get(otui.DateFormat.OTMM_DATETIME).parse(fldValue), otui.DateFormat.DATE);
						} catch (error) {
							//ignore parse errors
						}
					}
					labelVal = getSpanEl(fldValue);
				} else {
					labelVal = getSpanEl();
				}

				colEl.append(labelEl, labelVal);
			}
			//If not a singular column table, take the complete row width
			if (isTable) {
				colEl.style.width = "100%";
				if (i % 2 != 0) {
					divEl.append(rowEl);
					var rowEl = getRowEl();
					rowEl.append(colEl);
					divEl.append(rowEl);
					var rowEl = getRowEl();
				} else {
					rowEl.append(colEl);
					divEl.append(rowEl);
					var rowEl = getRowEl();
				}
				continue;
			} else {
				rowEl.append(colEl);
			}
			if (i % 2 != 0 || i + 1 == metadata.length) {
				divEl.append(rowEl);
				var rowEl = getRowEl();
			}
		}
		$(".otdcpw-body").append(divEl);
	}
	var getFgMetadata = function getFgMetadata(id) {
		var filteredFgData = [];
		var assetMetadata = this.properties.assetInfo.metadata.metadata_element_list;
		for (var i = 0; i < assetMetadata.length; i++) {
			if (assetMetadata[i].id == id) {
				var fgMetadata = this.properties.assetInfo.metadata.metadata_element_list[i].metadata_element_list;
				break;
			}
		}
		//Filter out non displayable fields
		var fgInfo = getFGInfo.call(this, id);
		for (var i = 0; i < fgMetadata.length; i++) {
			var fieldInfo = fgInfo.find(function (element) { return element.id == fgMetadata[i].id });
			if (fgMetadata[i].type == OTDCPWWorkspaceManager.METADATA_TABULAR && fieldInfo.metadata_element_list[0].displayable) {
				filteredFgData.push(fgMetadata[i]);
			}
			else {
				if (fieldInfo.displayable) {
					filteredFgData.push(fgMetadata[i]);
				}
			}
		}
		return filteredFgData;
	}
	var handleTabularMetadata = function handleTabularMetadata(metadataObj) {
		var colEl = getColumnEl();
		var labelEl = getLabelEl(otui.MetadataModelManager.getDisplayName(metadataObj.id));
		colEl.append(labelEl);
		//If singular column table, display values as buttons
		if (metadataObj.metadata_element_list.length == 1) {
			var div = document.createElement("div");
			div.setAttribute("class", "otdcpw-detail-col-div");
			var tabValues = metadataObj.metadata_element_list[0].values;
			for (var i = 0; i < tabValues.length; i++) {
				/*Create Button*/
				var buttonEl = document.createElement("button");
				if (tabValues[i].value) {
					buttonEl.textContent = tabValues[i].value.value || tabValues[i].value.display_value || tabValues[i].value.field_value.value;
				}
				buttonEl.setAttribute('type', "button");
				buttonEl.setAttribute('class', "otdcpw-detail-tabular-button");
				buttonEl.setAttribute('disabled', true);
				div.append(buttonEl);
			}
			colEl.append(div);
		}
		//Else display like normal table
		else {
			var tableEl = document.createElement("table");
			tableEl.setAttribute("class", "otdcpw-detail-table");
			var tableElHead = document.createElement("thead");
			tableElHead.setAttribute("class", "otdcpw-detail-theader");
			var tableElbody = document.createElement("tbody");
			var tableElthRow = document.createElement("tr")
			tableElthRow.setAttribute("class", "otdcpw-detail-trow");
			for (var i = 0; i < metadataObj.metadata_element_list[0].values.length; i++) {
				var tableElRow = document.createElement("tr");
				tableElRow.setAttribute("class", "otdcpw-detail-trow");
				for (var j = 0; j < metadataObj.metadata_element_list.length; j++) {
					if (i == 0) {
						var tableElth = document.createElement("th");
						tableElth.setAttribute("class", "otdcpw-detail-th");
						tableElth.style.width = 100 / metadataObj.metadata_element_list.length + "%";
						tableElth.innerText = metadataObj.metadata_element_list[j].name;
						tableElthRow.append(tableElth);
					}
					var tableElData = document.createElement("td");
					tableElData.setAttribute("class", "otdcpw-detail-td")
					tableElData.style.width = 100 / metadataObj.metadata_element_list.length + "%";
					if (metadataObj.metadata_element_list[j].values[i].value && metadataObj.metadata_element_list[j].values[i].value.value) {
						tableElData.innerText = metadataObj.metadata_element_list[j].values[i].value.value
					}
					tableElRow.append(tableElData);
				}

				tableElbody.append(tableElRow);
			}
			tableElHead.append(tableElthRow);
			tableEl.append(tableElHead);
			tableEl.append(tableElbody);
			colEl.append(tableEl);
		}
		return colEl;
	}
	var getColumnEl = function getColumnEl() {
		var colEl = document.createElement("div");
		colEl.setAttribute("class", "otdcpw-detail-grid-column");
		return colEl;
	}
	var getRowEl = function getRowEl() {
		var rowEl = document.createElement("div");
		rowEl.setAttribute("class", "otdcpw-detail-grid-row");
		return rowEl;
	}
	var getLabelEl = function getLabelEl(value, isMandatory) {
		var labelEl = document.createElement("label");
		labelEl.setAttribute("class", "otdcpw-detail-col-label");
		labelEl.innerText = value
		if (isMandatory) {
			var spanEl = document.createElement("span");
			spanEl.textContent = "*";
			spanEl.setAttribute("class", "otdcpw-mandatory-label");
			labelEl.append(spanEl);
		}
		return labelEl;
	}
	var getSpanEl = function getSpanEl(value) {
		var spanEl = document.createElement("span");
		if (value)
			spanEl.textContent = value;
		var div = document.createElement("div");
		div.setAttribute("class", "otdcpw-detail-col-div");
		div.append(spanEl);
		return div;
	}
	var getFGInfo = function getFGInfo(id) {
		var fgsInfo = this.properties.modelInfo.metadata_element_list;
		for (var i = 0; i < fgsInfo.length; i++) {
			if (fgsInfo[i].id == id) {
				var fgInfo = fgsInfo[i].metadata_element_list;
				break;
			}
		}
		return fgInfo;
	}

	otui.registerService('otdcpwAssets', {
		'read': function (data, callback, view) {
			if(!view) view = otui.main.getChildView(OTDCPWDetailView);

			let currentContext = localStorage.getItem(OTDCUtilities.currentContextName);
			//returning if the user does not have OTDC.PW.VIEW FET for PW and OTDCBW.VIEW for BW.
			if (!currentContext && ((currentContext === OTDCUtilities.PWContextName && !otui.UserFETManager.isTokenAvailable("OTDC.PW.VIEW")) ||
				(currentContext === OTDCUtilities.BWContextName && !otui.UserFETManager.isTokenAvailable("OTDCBW.VIEW")))) {
				view.storedProperty("no-results-msg", otui.tr("You do not have permission to view assets."));
				view.contentArea()[0].querySelector('.ot-results-header').remove();
				callback();
				return;
			}
			//view.blockContent();
			if (!data.nodeID) {
				data.nodeID = OTDCPWDetailView.nodeID;
			}
			var nodeID = data.nodeID;
			var pageID = nodeID;
			//PageManager.savePageData(pageID, data.pageProperties);
			//Overriding sort field
			data.pageProperties.sortField = "NAME";
			PageManager.savePageData(pageID, data.pageProperties);
			var preferenceID = PageManager.getFieldPreferenceName();
			var extraFields = PageManager.getExtraFields();
			var sendResults = function sendResults(response, success) {
				view.unblockContent();
				if (!success) {
					callback(response, success, 0);
				} else {
					let totalItems = view.properties.folderData?.container_child_counts?.total_child_count;
					callback(response, success, totalItems || 0);
					$('input[type="search"].uxa-text-input').attr('placeholder', otui.tr('Search within assets'));
					document.querySelectorAll('.uxa-multi-value-chip-label').forEach(function (label) {
						if (label.textContent.includes("Search for")) {
							label.closest('.uxa-multi-value-chip').style.display = 'none';
						}
					});
				}
			}

			if (data.appliedFacets)
				appliedFacets = JSON.parse(data.appliedFacets);

			let facetConfigurationName = '';
			let defaultFacetName='';
			if(currentContext){
				if(currentContext === OTDCUtilities.PWContextName){
					defaultFacetName = OTDCUtilities.defaultPWAssetFacetName;
					facetConfigurationName = (OTDCPWWorkspaceManager.config || {}).assetFacetConfig || defaultFacetName;
				} else if(currentContext === OTDCUtilities.BWContextName){
					defaultFacetName = OTDCUtilities.defaultBWAssetFacetName;
					facetConfigurationName = (OTDCBWWorkspaceManager.config || {}).assetFacetConfig || defaultFacetName;
				}
			}

			var hasFacets = !!(appliedFacets.facets.length);
			if (hasFacets) {
				if (!OTDCPWFacetsManager.currentFacetConfiguration) {
					OTDCPWFacetsManager.readFacets(facetConfigurationName, defaultFacetName, function () {
						OTDCPWAssetSearchManager.getFolderAssetsByKeyword("*", data.pageProperties.page, PageManager.assetsPerPage, preferenceID, extraFields, data, function (results, totalItems, success) {
							PageManager.numTotalChildren[pageID] = totalItems;
							sendResults(results, success);
						});
					});
				} else {
					OTDCPWAssetSearchManager.getFolderAssetsByKeyword("*", data.pageProperties.page, PageManager.assetsPerPage, preferenceID, extraFields, data, function (results, totalItems, success) {
						PageManager.numTotalChildren[pageID] = totalItems;
						sendResults(results, success);
					});
				}
			} else {
				if(!view){
					view = otui.main.getChildView(OTDCPWHomeView);
					if(!view){
						view = otui.main.getChildView(OTDCBWHomeView);
					}
				}
				OTDCUtilities.doFolderChildrenRead(data, sendResults, view);
			}
		}
	});

	var OTDCPWAssetsViewWrapper = exports.OTDCPWAssetsViewWrapper = otui.define("OTDCPWAssetsViewWrapper", ["withChildren"], function () {
		this.properties = {
			'name': 'OTDCPWAssetsViewWrapper', // Name of the view
			'title': otui.tr("Assets"),
			'type': 'OTDCPWAssetsViewWrapper'
		}
		this._initContent = function _initContent(self, callback) {
			var template = self.getTemplate("content");
			callback(template);
		}
	});

	var OTDCPWAssetsFolderView = exports.OTDCPWAssetsFolderView = otui.define("OTDCPWAssetsFolderView", function () {
		this.properties = {
			'name': 'OTDCPWAssetsFolderView', // Name of the view
			'title': otui.tr("Asset Library"),
			'type': 'OTDCPWAssetsFolderView'
		}
		this._initContent = function _initContent(self, callback) {
			var template = self.getTemplate("content2");
			callback(template);
			var parent = this.internalProperties.parentView.internalProperties.parentView.internalProperties.parentView;
		}
		this.bind("attached-to-view", function () {
			var detailView = this.internalProperties.parentView;
			if (detailView) {
				detailView.bind("got-subfolderinfo", loadSubFolderArea.bind(this));
			}
		});

		this.bind("show", function () {
			var parent = this.internalProperties.parentView.internalProperties.parentView.internalProperties.parentView;
			if (parent.internalProperties.childList && this.internalProperties.physicalContent) {
				this.properties.loaded = false;
				loadSubFolderArea.call(this);
			}
		});
	});

	var OTDCPWAssetsSidebarView = exports.OTDCPWAssetsSidebarView = otui.define("OTDCPWAssetsSidebarView", ["withAccordion"], function () {
		this.properties = {
			'name': 'OTDCPWAssetsSidebarView', // Name of the view
			'title': otui.tr("Left panel"),
			'type': 'OTDCPWAssetsSidebarView',
			'open': [0],//Index of the view - here it means 0th view(of this.hierarchy) should be open by default
			'searchConfigID': "",
			'nodeID': "",
			'appliedFacets': JSON.stringify({ 'facets': [] })
		}

		this._initContent = function _initContent(self, callback) {
			var template = self.getTemplate("content1");
			var content = $(template);

			if (self.properties.nodeID)
				self.getChildView(OTDCPWAssetsFacetsView).properties.needsUpdate = true;

			searchConfigList = SearchManager.searchConfigurations.response.search_configurations_resource.search_configuration_list;

			callback(content);
		};

		this.hierarchy = [{ 'view': OTDCPWAssetsFolderView }, { 'view': OTDCPWAssetsFacetsView }];


		this.watch('nodeID', function (newVal, oldVal) {
			if (newVal != oldVal) {
				if (this.properties.open[0] == 0) {
					this.getChildView(OTDCPWAssetsFacetsView).properties.facetsRendered = false;
					this.getChildView(OTDCPWAssetsFacetsView).properties.needsUpdate = true;
				}
				else
					this.getChildView(OTDCPWAssetsFacetsView).updateFacets();
			}
		});

		this.updateFacets = function () {
			this.getChildView(OTDCPWAssetsFacetsView).properties.isViewRendered = false;
			this.getChildView(OTDCPWAssetsFacetsView).updateFacets(JSON.parse(this.properties.appliedFacets).facets);
		};
	});



	/** Product Workspace Detail View - Assets screen */
	var OTDCPWAssetsView = exports.OTDCPWAssetsView =
		otui.extend("ResultsView", "OTDCPWAssetsView",
			function () {
				var pageprop = {
					'assetsPerPage': PageManager.assetsPerPage,
					'page': "0",
					'sortField': "NAME",
					'sortPrefix': "asc"
				};
				this.properties = {
					'name': 'OTDCPWAssetsView', // Name of the view
					'title': otui.tr("Assets"),
					'type': 'OTDCPWAssetsView',
					'sourceName': 'OTDCPWAssetsView',
					'lazyLoad': true,
					'selection_context': null,
					'service': 'otdcpwAssets',
					'serviceData': {},
					'templateName': PageManager.getResultsViewTemplate(),
					'minCellWidth': 320,
					'isMosaic': true,
					'pageProperties': pageprop,
					'no-results-msg': otui.tr("No Assets Found"),
					'loaded': false,
					'overridePageSizeTo': true,
					'searchConfigID': "none",
					'savedSearchID': "none",
					'appliedFacets': JSON.stringify({ 'facets': [] })
				};

				this.idProperty = 'folderID';
				this.serviceDataMap = {
					'nodeID': 'folderID', 'virtFolderId': 'virtFolderId',
					'pageProperties': 'pageProperties', 'appliedFacets': 'appliedFacets',
					'filterTerm': 'filterTerm', 'searchConfigID': 'searchConfigID',
					'searchScopeID': 'searchScopeID', 'metadataLocale': 'metadataLocale',
					'currentAccordion': 'currentAccordion', 'advancedSearch': 'advancedSearch',
					'savedSearchID': 'savedSearchID', 'searchID': 'searchID', 'breadcrumb': 'breadcrumb'
				};

				this._initContent = function _initContent(self, callback) {
					self.storedProperty("title", otui.tr("Assets"));
					self.storedProperty("sortOptions", RecentArtifactsManager.recentFoldersSortOptions || []);
					self.storedProperty("extraSortOptions", PageManager.searchSortOptions || []);
					self.storedProperty("newActionsLookupName", "OTDCPWDetailViewHeader");
					self.storedProperty("showRefresh", true); 
					self.storedProperty("showLocalSearch", true);
					//Set page properties
					this.properties.pageProperties = OTDCPWDetailView.getDefaultPageProperties();
					//This is used by Select All pages option
					this.selectionContext = {
						'type': 'com.artesia.asset.selection.FolderSelectionContext',
						'map': { 'folder_id': "folderID" },
						'alwaysUse': false,
						'includeFolderDescendents': true
					};
					this.internalProperties.arguments = arguments;
					var self = this;
					if (this.internalProperties.parentView.internalProperties.parentView.internalProperties.childList) {
						setTimeout(function () {
							loadAssetsResultsArea.call(self);
						}, 100);
					}
				};
				this.idProperty = function () {
					//Changing this logic as pagination not working as searchmanager is returing different ID when facets are selected
					// return SearchManager.getSearchResultID(this, 'folderID', true);
					return this.properties.folderID;
				};
				this.watch("folderID", function (newVal, oldVal) {
					if (newVal != oldVal) {
						var parentView = this.internalProperties.parentView;
						parentView.properties.folderID = newVal;
						parentView.getChildView(OTDCPWAssetsSidebarView).properties.nodeID = newVal;
					}
				});
				this.bind("attached-to-view", function () {
					this.unbind("detach");

				  this.bind("detach", function () {
				    try {
				      if (this.internalProperties && this.internalProperties.inAction) {
				        this.internalProperties.inAction = false;
				      }

				      // SAFE access to content area
				      const area = this.contentArea && this.contentArea();
				      const $area = area? (typeof area.find === "function" ? area : $(area)) : null;

				      if ($area && $area.length) {
				        const $results = $area.find(".ot-asset-results");
				        if ($results && $results.length) {
				          // Mirror the useful parts of the base cleanup, but null-safe
				          $results.off();
				          if ($results.is(":hidden")) $results.show();
				          const park = document.querySelector("#single-spa-container");
				          if (park) park.appendChild($results[0]);
				        }
				      }
				    } catch (err) {
				      console.warn("Custom detach: non-fatal error ignored", err);
				    }
				});
					
					
					var parentView = this.internalProperties.parentView;

					if (parentView)
						parentView.bind("got-subfolderinfo", loadwithdelay.bind(this));
				});
				this.bind("reload-data", function () {
					if (window['OTDCPWAssetsSidebarView']) {
						var view = this.internalProperties.parentView.getChildView(OTDCPWAssetsSidebarView);
						if (view) {
							//Update sidebar facets when view is reloaded, same as Homeview
							view.properties.appliedFacets = this.properties.appliedFacets;
							var facets = document.querySelector(".ot-facets-root .ot-facet");
							if (facets) {
								SearchManager.clearSearchForKeyword(this.properties.folderID);
								view.updateFacets();
							}
						}
					}
				});
				this.bind("single-spa-results-loaded", function () {
					let otResults = this.contentArea()[0].querySelector(".ot-asset-results");
					if (otResults) {
						let view = this;
						$(otResults).on("applied-facets-change", function (event) {
							let eventDetail = event.detail;
							if (eventDetail.appliedFacets.length === 0) {
								OTDCPWAssetsFacetsView.handleClearFilters(view);
							} else if (eventDetail.isLocalSearch) {
								OTDCPWAssetsFacetsView.performKeywordSearch(view, eventDetail);
							} else {
								OTDCPWAssetsFacetsView._removeFacet(view, eventDetail);
							}
						});
					}
				});
				this.bind("setup-finished", function () {
					//Set the assets view title same as subfolder selected - This will be called for first time
					//Title is set here because when sub-foldername is available, then title tag is not available
					$(".otdcpw-detail-assets-title ot-i18n").attr("ot-token", this.properties.folderName);
				});
			});

	function loadSubFolderArea() {
		var parent = this.internalProperties.parentView.internalProperties.parentView.internalProperties.parentView;
		var assetsWrapper = this.internalProperties.parentView.internalProperties.parentView;
		var assetsView = assetsWrapper.getChildView(OTDCPWAssetsView);
		var childList = parent.internalProperties.childList;
		//Check if tab selected is Metadata
		if (parent.properties.OTDCPWDetailView_selected != "OTDCPWAssetsViewWrapper" || this.properties.loaded) {
			return;
		}
		this.properties.loaded = true;
		//ResultsView._initContent.apply(assetsView, assetsView.internalProperties.arguments);
		//If no subfolder then exit
		if (childList.length == 0) {
			return;
		}
		var isFirstEl = true;

		for (var i = 0; i < childList.length; i++) {
			if (childList[i].type != OTDCPWWorkspaceManager.CONTAINER)
				continue;
			var navEl = getNavOption(childList[i].asset_id, childList[i].name, OTDCPWAssetsView.handleFolderSel);
			if (isFirstEl) {
				isFirstEl = false;
				var divEl = document.createElement("div");
				divEl.setAttribute("class", "otdcpw-preview-content");
			}
			if (childList[i].asset_id == assetsView.properties.folderID) {
				navEl.setAttribute("class", "otdcpw-detail-side-div otdcpw-detail-asset-folder-active");
				assetsView.properties.breadcrumb = { 'ids': [childList[i].asset_id], 'names': [childList[i].name] }
			}
			divEl.append(navEl);
		}
		$(".otdcpw-asset-subfolder-view").empty();
		$(".otdcpw-asset-subfolder-view").append(divEl);

	}

	function loadAssetsResultsArea() {
		var parent = this.internalProperties.parentView.internalProperties.parentView;
		//Check if tab selected is Metadata
		if (parent.properties.OTDCPWDetailView_selected != "OTDCPWAssetsViewWrapper" || this.properties.loaded) {
			return;
		}
		this.properties.loaded = true;
		ResultsView._initContent.apply(this, this.internalProperties.arguments);
	}
	function loadwithdelay() {
		var self = this;
		setTimeout(() => {
			loadAssetsResultsArea.call(self);
		}, 100);
	}
	OTDCPWAssetsView.setUPUploadAssets = function setUPUploadAssets(parentView) {
		var activeSubFolder = this.properties.folderID;
		//Get folder type of active sub folder
		var subFolders = parentView.internalProperties.childList;
		var folderType, isDeleted;
		for (i = 0; i < subFolders.length; i++) {
			if (subFolders[i].asset_id == activeSubFolder) {
				folderType = subFolders[i].container_type_id;
				isDeleted = subFolders[i].deleted;
				break;
			}
		}
		if (FolderTypesManager.canUploadAssets(folderType) && !isDeleted) {
			return true;
		} else {
			return false;
		}
	}

	OTDCPWAssetsView.handleFolderSel = function handleFolderSel(event) {
		var oldActiveEl = $(".otdcpw-detail-asset-folder-active");
		oldActiveEl.removeClass("otdcpw-detail-asset-folder-active");
		event.currentTarget.setAttribute("class", "otdcpw-detail-side-div otdcpw-detail-asset-folder-active");
		var folderView = otui.Views.containing(event.currentTarget);
		var wrapperView = folderView.internalProperties.parentView.internalProperties.parentView;
		var assetsView = wrapperView.getChildView(OTDCPWAssetsView);
		//Below are required to fetch assets - so set it with active folder id
		assetsView.properties.serviceData.nodeID = event.currentTarget.id;
		assetsView.properties.folderID = event.currentTarget.id;
		//Set breadcrumb which will be used as target folder by upload assets dialog
		assetsView.properties.breadcrumb = { 'ids': [event.currentTarget.id], 'names': [event.currentTarget.querySelector("span").textContent] }
		//Check if user has permission to upload - TODO

		//Set selected folder name as title of assets view
		if (event.currentTarget.innerText) {
			assetsView.properties.folderName = event.currentTarget.innerText;
			$(".otdcpw-detail-assets-title ot-i18n").attr("ot-token", event.currentTarget.innerText);
		} else {
			$(".otdcpw-detail-assets-title ot-i18n").attr("ot-token", "Assets");
		}

		//Revaluate upload assets button 
		document.querySelectorAll('ot-point[ot-lookup=OTDCPWDetailViewHeader]').forEach(function (point) {
			point.reevaluate(); //This method calls setup method of this ot-point OTDCPWDetailViewHeader.setupUploadAsset
		});
		if (assetsView)
			assetsView.reload();
	}
	/****************************************************************************************************************/
	//Product Workspace Detail View - Security Policies screen
	function render() {
		var contentArea = this.contentArea()[0];

		if (this.properties.edit && !this.internalProperties.hasSecurityPermission) {
			otui.empty(contentArea);
			contentArea.appendChild(this.getTemplate("noedit"));
		} else {
			var asset = this.internalProperties.asset;
			var securityPolicies = asset.security_policy_list;
			if (this.properties.edit) {
				var userEditablePolicies = UserSecurityPolicyManager.getEditableSecurityPolicies();
				var assignedEditableList = [];
				for (var index in securityPolicies) {
					var id = securityPolicies[index].id;
					if (userEditablePolicies[id] && userEditablePolicies[id].id === id) {
						assignedEditableList.push(userEditablePolicies[id]);
					}
				}
				securityPolicies = assignedEditableList;
			}

			if (securityPolicies) {
				contentArea.querySelector("ot-security-policy").assignedPolicies = securityPolicies;
			}
		}
		this.storedProperty("rendered", true);
	};

	function loadSecurityPolicies(parent, asset) {
		//Check if active tab is security View else return
		if (parent.properties.OTDCPWDetailView_selected != "OTDCPWDetailSecurityView") {
			return;
		}
		this.internalProperty("asset", this.internalProperties.parentView.internalProperties.asset);
		render.call(this);
	}

	var OTDCPWDetailSecurityView = exports.OTDCPWDetailSecurityView =
		otui.define("OTDCPWDetailSecurityView", ["withContent"],
			function () {
				this.properties = {
					'name': 'OTDCPWDetailSecurityView', // Name of the view
					'title': otui.tr("Security Policies"),
					'type': 'OTDCPWDetailSecurityView',
					'edit': false
				};
				this._initContent = function _initContent(self, callback) {
					self.storedProperty("title", otui.tr("Security Policies"));
					this.internalProperty("hasSecurityPermission", true);//TODO
					var content = this.getTemplate(this.properties.edit ? "edit-content" : "content");
					callback(content);
					this.internalProperty("asset", this.internalProperties.parentView.internalProperties.asset);
					if (this.internalProperties.asset)
						render.call(this);

				}
				this.bind("show", function () {
					if (this.storedProperty("rendered")) {
						var contentArea = this.contentArea();
						if (this.properties.edit) {
							var securityVisibleDiv = contentArea.find(".ot-security-visible");
							if (securityVisibleDiv.length > 0) {
								otui.OTMMHelpers.fixSecurityPolicyFiltersPosition(contentArea, contentArea);
							}
						}
					}
				});

				this.bind("attached-to-view", function () {
					var parentView = this.internalProperties.parentView;

					if (parentView)
						parentView.bind("got-asset", loadSecurityPolicies.bind(this));
				});
			});

	
	var OTDCPWDetailView = exports.OTDCPWDetailView =
		otui.define("OTDCPWDetailView", ['withTabs'], function () {
			const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms));
			this.properties = {
				'name': 'OTDCPWDetailView', // Name of the view
				'title': otui.tr("Product Workspace Detail View"),
				'type': 'OTDCPWDetailView',
				'appliedFacets': JSON.stringify({ 'facets': [] }),
				'searchID': '*'
			};
			//Variable to hold order of tabs
			this.hierarchy =
				[
					{ 'view': OTDCPWAssetsViewWrapper },
					{ 'view': OTDCPWDetailMetadataView },
					{ 'view': OTDCPWRelatedWorkspacesView, 'if': async function () { 
						await delay(500);
						return otui.UserFETManager.isTokenAvailable("OTDC.VIEW.RELATEDWORKSPACES") && OTDCUtilities.isEAMContext();
					}},
						//return otui.UserFETManager.isTokenAvailable("OTDC.VIEW.RELATEDWORKSPACES") && OTDCUtilities.isEAMContext(); } },
					{ 'view': OTDCPWDetailSecurityView, 'if': function () { return otui.UserFETManager.isTokenAvailable("SECURITY_POLICY_VIEW"); } }
				];

			this._initContent =
				// Note that initContent functions must always have a name.
				function initRecentView(self, placeContent) {
					self.storedProperty("title", otui.tr("Product Workspace Detail View"));
					OTDCPWDetailView.nodeID = this.properties.nodeID || this.properties.resourceID;
					OTDCPWDetailView.currentContext = self.internalProperty("parentView");
					OTDCPWDetailView.editMode = this.properties.editMode;
					// get the template with the id="content"
					var template = this.getTemplate("content");
					//place the template data into the UI.
					placeContent(template);
					//Add child views to Assets wrapper tab
					var wrapperView = self.getChildView(OTDCPWAssetsViewWrapper);
					wrapperView.addChildView(new OTDCPWAssetsSidebarView());
					wrapperView.addChildView(new OTDCPWAssetsView());
					getWorkspaceMetadata.call(self);
					getFolderChildren.call(self);
					
					// Set localized title for Sync button
			        var syncBtn = document.querySelector('.otdcpw-detail-icon-nonsync');
			        if (syncBtn) {
			            syncBtn.setAttribute('title', otui.tr('Sync'));
			        }
			        // Set localized title for Edit button
			        var editBtn = document.querySelector('.otdcpw-detail-icon-edit');
			        if (editBtn) {
			            editBtn.setAttribute('title', otui.tr('Edit'));
			        }
				};
			this.watch("selected", function (tab) {
				if (tab == "OTDCPWDetailMetadataView" || tab == "OTDCPWDetailSecurityView") {
					var el = $(".otdcpw-preview-content").detach();
					if (!this.internalProperties.preview) {
						this.internalProperties.preview = el;
					}
				}
				if (tab == "OTDCPWAssetsView") {
					$(".ot-inspectorview-content").prepend(this.internalProperties.preview);
				}

			});

		}); // END: OTDCPWDetailView definition block
	function getWorkspaceMetadata() {
		var self = this;
		if (OTDCPWDetailView.nodeID) {
			OTDCPWDetailMetadataView.assetID = OTDCPWDetailView.nodeID;
		}
		var data = { 'assetID': OTDCPWDetailMetadataView.assetID, 'unwrap': true };
		//self.blockContent();
		otui.services.asset.read(data,
			function (asset, success) {
				self.unblockContent();
				if (!success) {
					var msg;
					if (asset.exception_body) {
						if (asset.exception_body.http_response_code == 404)
							msg = otui.tr("Either the workspace does not exist or you do not have permission to view the workspace. Please contact your administrator.")
						else
							msg = asset.exception_body.message;
					}
					if (msg) {
						otui.NotificationManager.showNotification({
							'message': msg,
							'status': 'error',
							'stayOpen': true
						});
					}
				} else {
					self.internalProperty("asset", asset);
					//Display Model name
					var metadataTitle = otui.tr("Metadata");
					if (asset && asset.metadata_model_id)
						metadataTitle = otui.tr("Metadata: {0}", otui.MetadataModelManager.getModelDisplayName(asset.metadata_model_id));
					//Get metadata view
					var metadataView = self.getChildView(OTDCPWDetailMetadataView);
					metadataView.properties.title = metadataTitle;
					self.storedProperty("title", otui.tr(metadataTitle));
					//Store asset info for further use
					metadataView.properties.assetInfo = asset;
					//Get Model Information		
					metadataView.properties.modelInfo = otui.MetadataModelManager.getModelByName(asset.metadata_model_id);
					//Set workspace title
					$("div.otdcpw-workspace-name").text(asset.name);
					//Set thumbnail if exists
					var url = asset.rendition_content && asset.rendition_content.thumbnail_content && asset.rendition_content.thumbnail_content.url;
					if (url)
						$("ot-rendition.otdcpw-asset-thumbnail-icon").attr("src", url);
					//Get permission info
					var permissions = [];
					if (asset.access_control_descriptor && asset.access_control_descriptor.permissions_map)
						permissions = asset.access_control_descriptor.permissions_map.entry || [];
					self.internalProperty("permissions", permissions);
					var fieldObject = otui.MetadataModelManager.rationaliseFieldObject({ 'id': 'ARTESIA.FIELD.ASSET NAME' });
					var editPermission = AssetDetailManager.getMetadataEditPermission(permissions);
					var hasEditPermission = asset.type == "com.artesia.container.Container" || asset.content_sub_type == "CLIP" ? editPermission : editPermission && fieldObject && fieldObject.editable;
					self.internalProperties.hasEditPermission = hasEditPermission;
					//Hide Edit button if no edit permission
					if (!hasEditPermission || !otui.UserFETManager.isTokenAvailable("FOLDER.EDIT_PROPERTIES.SINGLE")) {
						$(".otdcpw-detail-icon-edit").css("display", "none");
					}
					//Populate locked by information 
					setupLocked.call(self.contentArea()[0], asset);

					//Hide sync button if hybris connection is not available for PW
					let currentContext = localStorage.getItem(OTDCUtilities.currentContextName);
					if (currentContext && currentContext === OTDCUtilities.PWContextName && OTDCPWWorkspaceManager.connection != "") {
						$("#otdcpw-sync-button").show();
					}
					//Sync or non sync indicator
					if (OTDCPWDetailView.isSynced(asset)) {
						var icon = $(".otdcpw-detail-icon-nonsync")[0];
						icon.setAttribute("class", "otdcpw-detail-icon-sync");
						icon.setAttribute("aria-label", otui.tr("Resync"));
						icon.setAttribute("title", otui.tr("Resync"));
					}
					self.trigger("got-asset", asset);
				}
			});
	}
	function setupLocked(resource) {
		if (resource && resource.locked) {
			// TODO: Server response must return metadata_lock_state_user_id..(ART-35892).. Once it gets fixed please check against
			// metadata_lock_state_user_id instead of metadata_lock_state_user_name.
			this.querySelector(".otdcpw-folder-status").style.display = "flex";
			if (resource.metadata_lock_state_user_name === JSON.parse(sessionStorage.session).login_name) {
				this.querySelector("[ot-text]").textContent = otui.tr("Locked by me");
			}
			else {
				var self = this;
				UserDetailsManager.getUserDetailsByID(resource.metadata_lock_state_user_name, function (userDetails) {
					self.querySelector("[ot-text]").textContent = otui.tr("Locked by {0}", userDetails.name);
				});
			}
		}
	}
	function getFolderChildren() {
		var self = this;
		var data = {};
		var selectionContext = {
			'load_asset_content_info': true
		};
		data.selection = {
			'data_load_request': selectionContext
		};
		data.id = OTDCPWDetailView.nodeID;
		OTDCPWWorkspaceManager.readFolderChildren(data, function callback(response, success) {
			if (!success) {
				//TODO Handle error
			} else {
				self.internalProperties.childList = response;
				var wrapperView = self.getChildView(OTDCPWAssetsViewWrapper);
				var assetsView = wrapperView.getChildView(OTDCPWAssetsView);
				//Set node ID as first folder
				var isFirstEl = true;
				for (var i = 0; i < response.length; i++) {
					if (response[i].type != OTDCPWWorkspaceManager.CONTAINER)
						continue;
					//By default first element will be active
					if (isFirstEl) {
						isFirstEl = false;
						assetsView.properties.serviceData.nodeID = response[i] && response[i].asset_id;
						assetsView.properties.folderID = response[i] && response[i].asset_id;
						assetsView.properties.folderName = response[i] && response[i].name;//This will be used later to set title of assets view
					}
				}
				self.trigger("got-subfolderinfo");
			}
		});
	}
	OTDCPWDetailView.onClickEdit = function onClickEdit(event) {
		var view = otui.Views.containing(event.currentTarget);
		var params = {};
		var tab;
		params["resourceID"] = view.properties.nodeID;
		//Map the tab view selected from edit to view
		if (view.properties.OTDCPWDetailView_selected == "OTDCPWDetailMetadataView") {
			tab = "OTDCPWDetailMetadataEdit";
		} else
			if (view.properties.OTDCPWDetailView_selected == "OTDCPWAssetsViewWrapper") {
				tab = "OTDCPWAssetsWrapperEdit";
			} else
				if (view.properties.OTDCPWDetailView_selected == "OTDCPWDetailSecurityView") {
					tab = "OTDCPWDetailSecurityEdit";
				}
		params["OTDCPWDetailEditView_selected"] = tab;
		//Route to OTDCPWDetailEditView
		view.callRoute("edit", params, true);
	}

	OTDCPWDetailView.collapseAndExpandSidebar = function collapseAndExpandSidebar() {
		document.querySelector('ot-view-collapser').click();
	}

	OTDCPWDetailView.tabClickListener = function tabClickListener(event) {
		if (event.target.tagName === 'OT-VIEW-COLLAPSER') {
			if ($('.sidebar-expand-control-wrapper').length == 0) {
				let expandButton = $('<div class="sidebar-expand-control-wrapper" onclick="OTDCPWDetailView.collapseAndExpandSidebar()"> <span class="sidebar-expand-control-icon" title="Show left panel"><ot-i18n ot-as-title="" ot-token="Show left panel" parsed="true"></ot-i18n></span> </div>')
				$('.ot-left-toolbar-section').prepend(expandButton);
			} else {
				$('.sidebar-expand-control-wrapper').remove();
			}
		}
	}


	OTDCPWDetailView.onClickSync = function onClickSync(event) {
		var srcView = otui.Views.containing(event.currentTarget);
		var data = {
			"asset": srcView.internalProperties.asset,
			"srcView": srcView
		}
		if (OTDCPWDetailView.isSynced(srcView.internalProperties.asset)) {
			OTDCPWResyncConfirmView.show(data)
		} else {
			OTDCPWSyncView.show(data);
		}
	}

	OTDCPWDetailView.isSynced = function (workspace) {
		return OTDCPWAssetActionsManager.getMetadataFieldValue(workspace, 'OTDC.PW.PK', 'OTDC.PW.FG.INFO')
			&& OTDCPWAssetActionsManager.getMetadataFieldValue(workspace, 'OTDC.PW.ARTICLE.NUMBER', 'OTDC.PW.FG.INFO')
			&& OTDCPWAssetActionsManager.getMetadataFieldValue(workspace, 'OTDC.PW.CATALOGVERSION', 'OTDC.PW.FG.CLASSIFICATION');
	}

	OTDCPWDetailView.onClickCancel = function onClickCancel(event) {
		var view = otui.Views.containing(event.currentTarget);
		let currentContext = localStorage.getItem(OTDCUtilities.currentContextName);
		if(currentContext && currentContext === OTDCUtilities.PWContextName){
			view.callRoute("cancelPW");
		} else if(currentContext && currentContext === OTDCUtilities.BWContextName){
			view.callRoute("cancelBW");
		}
	}

	OTDCPWDetailView.getDefaultPageProperties = function getDefaultPageProperties(event) {
		var pageprop = {
			'assetsPerPage': PageManager.assetsPerPage,
			'page': "0",
			'sortFieldforFacets': PageManager.DEAFAULT_CHECKED_OUT_SORT_FIELD,
			'sortField': "NAME",
			'sortPrefix': PageManager.ascendingSortPrefix
		};

		return pageprop;
	}
	OTDCPWDetailView.PWDetailRouter = function (req, routeParts, routeData) {
		//var nodeID = routeData.nodeID || req.params.resourceID;
		return {
			'name': 'OTDCPWDetailView_' + routeData.nodeID || req.params.resourceID,
			'nodeID': routeData.nodeID || req.params.resourceID,
			'resourceID': req.params.resourceID,
			'pageProperties': routeData.pageProperties,
			'editMode': false
		};
	};

	OTDCPWDetailView.PWDetailShowRouter = function (req, routeParts, routeData) {
		return {
			'name': 'OTDCPWDetailView_' + req.params.resourceID,
			'nodeID': req.params.resourceID,
			'resourceID': req.params.resourceID,
			'editMode': req.params.editMode || false,
			'appliedFacets': routeData.appliedFacets,
			'pageProperties': routeData.pageProperties
		};
	};

	OTDCPWDetailView.PWDetailEditRouter = function (req, routeParts, routeData) {
		return {
			'name': 'OTDCPWDetailView_' + req.params.resourceID + 'edit',
			'nodeID': req.params.resourceID,
			'resourceID': req.params.resourceID,
			'editMode': req.params.editMode || true
		};
	}

	OTDCPWDetailView.ModernUIHeaderParameters = {'isModernResultsView': true, 'showBreadcrumbPanel': false};

	//TODO - Change router name pwdetail
	OTDCPWDetailView.route.base = "/:nodeID@:OTDCPWDetailView_selected?@:fromView?";
	OTDCPWDetailView.route.define("go", OTDCPWDetailView.route.as("otdcWorkspace" + OTDCPWDetailView.route.base,
		otui.withTemplate("main-with-actionbar"),
		OTDCPWDetailView.route.to(OTDCPWDetailView.PWDetailRouter),
		{...OTDCPWDetailView.ModernUIHeaderParameters, 'pageTitle': otui.tr('Workspace details')}
	));

	OTDCPWDetailView.route.define("show", OTDCPWDetailView.route.as("otdcWorkspaceView/:resourceID@:OTDCPWDetailView_selected?@:fromView?",
		otui.withTemplate("main-with-actionbar"),
		OTDCPWDetailView.route.to(OTDCPWDetailView.PWDetailShowRouter),
		{...OTDCPWDetailView.ModernUIHeaderParameters, 'pageTitle': otui.tr('Workspace details')}
	));

	OTDCPWDetailView.route.define("edit", OTDCPWDetailEditView.route.use("edit", true), {...OTDCPWDetailView.ModernUIHeaderParameters});
	OTDCPWDetailEditView.route.define("show", OTDCPWDetailView.route.use("show", true), {...OTDCPWDetailView.ModernUIHeaderParameters});
	// Then bind that route URL into the named route "open".
	OTDCPWDetailView.route.define("open", OTDCPWDetailView.route.use("go", [OTDCPWDetailView]), {...OTDCPWDetailView.ModernUIHeaderParameters});
	//Route for on changing tabs from Metadata to Asset or Security, URL gets updated
	OTDCPWDetailView.route.define("change-tab", function (routeName, params, viewProps) {
		OTDCPWDetailView.route.update("go")(routeName, params, viewProps);
	}, {...OTDCPWDetailView.ModernUIHeaderParameters});
	//Route for pagination in Assets view
	OTDCPWAssetsView.route.define("change-page", function (routeName, params) {
		this.properties.pageProperties = params.pageProperties;
		//this.reload();
	}, {...OTDCPWDetailView.ModernUIHeaderParameters})
	//Route for Asset Inspector View - View mode
	OTDCPWDetailView.route.define("open-contents", InspectorView.route.use("open", {...OTDCPWDetailView.ModernUIHeaderParameters, 'pageTitle': otui.tr('Workspace details')}));
	//Route for Asset Inspector Edit View
	OTDCPWDetailView.route.define("edit-contents", InspectorEditView.route.use("open"), {...OTDCPWDetailView.ModernUIHeaderParameters, 'pageTitle': otui.tr('Edit workspace details')});
	//Route for double click of folder
	OTDCPWDetailView.route.define("open-folder", FolderResultsView.route.use("open"), {...OTDCPWDetailView.ModernUIHeaderParameters});

	otui.ready(
		function () {
			//Logic to route back from Inspector View to the Detail view
			function linkToInspectorView(source) {
				/* This should be read as "Link FolderResultsView to ReviewTableView" by:
					* 1: Generating a linked "go" route for when the FolderResultsView is opened by the ReviewTableView.
					* 	The URL for this route is the URL for the source's "open" route, and the base URL for the InspectorView added.
					*  When the URL matches this, do the same actions as for the standard "go" route, but also give extra parameters in the form of the "options" object.
					*  Once this has been done, defining a route for the source which calls the "go" route of the InspectorView (Or any other route which uses "go" as a base) will follow this URL.
					* 2: Generating a linked "back" route for when the InspectorView was opened by the source. When that happens, the "open" route for the source is called.
					*/
				InspectorView.route.link(source, { 'go': 'open' }, { 'back': 'open' }, undefined, { 'props_exclude_sanitization': ["breadcrumb"], 'pageTitle': otui.tr('Asset details') });
				InspectorEditView.route.link(source, { 'go': 'open' }, { 'back': 'open' }, undefined, { 'props_exclude_sanitization': ["breadcrumb"], 'pageTitle': otui.tr('Asset editor') });
			};
			[OTDCPWDetailView].forEach(linkToInspectorView);
		}
	);
	//**Few routes for this view are defined in OTDCPWHomeView as there is some dependency on it

})(window);