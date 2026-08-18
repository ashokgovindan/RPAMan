package rpa.rpaman;

/** A developer who can own projects and change requests, and carry adhoc work. */
public class Developer {

    /** Database id; 0 means the row has not been saved yet. */
    public int id;

    /** Employee identifier from the HR system; free text so any format works. */
    public String empId = "";

    public String name = "";
    public String email = "";
    public boolean active = true;

    @Override
    public String toString() {
        return name;
    }
}
