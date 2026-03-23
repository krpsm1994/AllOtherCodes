export class GlobalVariables {

    public static readonly DAMLINK_VERSION = "CE 26.2";
    // this variable is set or unset based on the local unSavedData variable present in each of the editor component.
    private static dataChanged: boolean;

    static get unSavedData(): boolean {
        return this.dataChanged;
    }

    static set unSavedData(val: boolean) {
        this.dataChanged = val;
    }
}
