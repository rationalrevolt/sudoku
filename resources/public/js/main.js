$(function () {
	var sud_cell = $("<td class='sudcell'></td>");
	var sud_row = $("<tr class='sudrow'></tr>");
	var sud_table = $("<table class='sudtable'></table>");
	var sud_maybecell = $("<div class='sudmaybe'></div>");
	
	init();
	
	function contains(arr,e) {
		var i,v;
		
		for(i=0;i<arr.length;i++) {
			v = arr[i];
			if (e[0] === v[0] && e[1] === v[1]) 
			return true;
		}
		
		return false;
	}
	
	function init() {
		$("#hint").click(function() {
			$.ajax({type: "POST",
			 		url: "/getHint"}).done(function(data) {
				var loc = data.location;
				var val = data.value;
				var e = $("#cell" + loc[0] + loc[1]);
				setCellValue(e,val,{hinted:true});
			});	
		});
		
		$("#check").click(function() {
			$.ajax({type: "POST", url: "/checkBoard"}).done(refresh);	
		});
		
		$("#reset").click(function() {
			$.ajax({type: "POST", url: "/resetBoard"}).done(refresh);
		});
		
		refresh();
	}
	
	function refresh() {
		$.when(
			$.ajax({type: "POST",
			 		url: "/getBoard"}),
		    $.ajax({type: "POST",
			       	url: "/getOriginals"}),
			$.ajax({type: "POST",
			       	url: "/getHints"}),
			$.ajax({type: "POST",
			       	url: "/getErrors"})       	       	
		).done(function(r1,r2,r3,r4) {
			createBoard(r1[0],r2[0],r3[0],r4[0]);
		});
	}
	
	function createBoard(board,originals,hints,errors) {
		var nums = _.range(9);
		var table = sud_table.clone();
		
		$("#sudokuboard").empty();
		$("#sudokuboard").append(table);
		
		_.each(nums,function(i) {
			var row = sud_row.clone();
			$(table).append(row);
			_.each(nums,function(j) {
				var e = sud_cell.clone();
				var loc = [i,j];
				var isEmpty = board[i][j] === 0;
				var isOriginal = contains(originals,loc);
				var isHinted = contains(hints,loc);
				var isBadmove = contains(errors,loc);
				
				$(row).append(e);
				
				if (isOriginal) 
					$(e).addClass("original");
				else if (((i*9)+j) % 2 === 0)
					$(e).addClass("odd");
				else
					$(e).addClass("even");	
				
				if (isHinted) 
					$(e).addClass("hinted");				
				else if (isBadmove) 
					$(e).addClass("badmove");
				
				$(e).html(board[i][j] || "");
				$(e).attr("id","cell" + i + j);
				$(e).data("loc",loc);
				
				if (isEmpty) {
					setCellEmpty(e);
				} else if (!isEmpty && !isOriginal && !isHinted) {
					$(e).click(function() {
						sendSelection(e,0);
					});
				}
			});
		});
	}
	
	function setCellValue(e,val,result) {
		$(e).removeClass("badmove");
		if (val === 0) {
			setCellEmpty(e);
		} else {
			$(e).html(val);
			if (result.hinted === true) {
				$(e).addClass("hinted");
				$(e).off("click");
			} else {
				$(e).click(function() {
					sendSelection(e,0);
				});
				if (result.validMove === false) {
					$(e).addClass("badmove");
				}
			} 
		}
	}
	
	function setCellEmpty(e) {
		var nums = _.range(9);
		
		$(e).empty();
		$(e).off("click");
		
		_.each(nums,function(val) {
			var m = sud_maybecell.clone();
			$(e).append(m);
			$(m).html(val+1);
			$(m).click(function(ev) {
				sendSelection(e,val+1);
				ev.stopPropagation();
			});
		});
	}
	
	function sendSelection(e,val) {
		$.ajax({type: "POST",
				contentType: "application/json",
				url: "/updateBoard",
				data: JSON.stringify({location: e.data().loc, value: val})
		}).done(function(result) {
			setCellValue(e,val,result);
			if (result.solved) {
				solved();
			}
		});
	}
	
	function solved() {
		$(".sudtable").addClass("solved");
		$(".sudcell").removeClass("original hinted even odd").off("click");
	}
});
