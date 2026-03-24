// Fuel Bill Generator - PDF Export Functionality

/**
 * Export the fuel receipt to PDF using html2pdf library
 */
function exportToPDF() {
    const element = document.getElementById('Template1');
    const receiptNumber = document.querySelector('.invoice_no').textContent;
    const filename = `Fuel_Receipt_${receiptNumber}.pdf`;

    // Configure PDF options
    const options = {
        margin: 10,
        filename: filename,
        image: { type: 'jpeg', quality: 0.98 },
        html2canvas: { 
            scale: 2,
            useCORS: true,
            logging: false,
            letterRendering: true
        },
        jsPDF: { 
            unit: 'mm', 
            format: 'a4', 
            orientation: 'portrait' 
        }
    };

    // Generate and download PDF
    html2pdf().set(options).from(element).save();
}

/**
 * Alternative method using browser's print functionality
 */
function printReceipt() {
    window.print();
}

// Initialize when DOM is loaded
document.addEventListener('DOMContentLoaded', function() {
    console.log('Fuel Receipt Generator loaded');
});
