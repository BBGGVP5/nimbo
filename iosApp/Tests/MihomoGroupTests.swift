import Foundation

@main struct MihomoGroupTests {
    static func main() {
        let rows: [[String: Any]] = [
            ["name":"YouTube","type":"select","proxies":["FI","FI","DIRECT",""]],
            ["name":"Auto","type":"url-test","proxies":["FI"]],
            ["name":"Hidden","type":"fallback","hidden":true,"proxies":["FI"]],
            ["name":"Broken","type":"select","proxies":42],
            ["name":"YouTube","type":"select","proxies":["DE"]]
        ]
        let declared = NimboMihomoGroup.declared(rows,selections:["YouTube":"missing","Auto":"FI"])
        precondition(declared.map(\.name) == ["YouTube","Auto"])
        precondition(declared[0].members == ["FI","DIRECT"] && declared[0].current == nil)
        precondition(declared[0].selectable && !declared[1].selectable)
        let runtime: [String:[String:Any]] = [
            "Auto":["type":"URLTest","all":["DE"],"now":"DE"],
            "YouTube":["type":"Selector","all":["DIRECT","FI"],"now":"FI"],
            "Hidden":["type":"Fallback","hidden":true,"all":["FI"],"now":"FI"],
            "New":["type":"Selector","all":["US"],"now":"missing"]
        ]
        let live = NimboMihomoGroup.live(runtime,declaredOrder:declared.map(\.name))
        precondition(live.map(\.name) == ["YouTube","Auto","New"])
        precondition(live[0].current == "FI" && live[2].current == nil)
        precondition(NimboMihomoGroup.active(live,name:"removed")?.name == "YouTube")
        precondition(NimboMihomoGroup.active([],name:"anything") == nil)
        print("Mihomo category ordering, hidden/malformed filtering, selection and refresh fallback passed")
    }
}
